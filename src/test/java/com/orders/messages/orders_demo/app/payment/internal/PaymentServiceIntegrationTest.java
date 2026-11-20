package com.orders.messages.orders_demo.app.payment.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.order.internal.*;
import com.orders.messages.orders_demo.app.order.internal.exceptions.*;
import com.orders.messages.orders_demo.app.payment.api.*;
import com.orders.messages.orders_demo.app.payment.internal.exceptions.*;

class PaymentServiceIntegrationTest extends PostgresContainer {
    @Autowired
    private PaymentAttemptService paymentService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private PaymentAttemptRepository paymentRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private CustomerRepository customerRepository;
    private final List<UUID> paymentIds = new ArrayList<>();
    private final List<UUID> orderIds = new ArrayList<>();
    private final List<UUID> customerIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            paymentIds.forEach(paymentRepository::deleteById);
            paymentRepository.flush();
            orderIds.forEach(orderRepository::deleteById);
            orderRepository.flush();
            customerIds.forEach(customerRepository::deleteById);
            customerRepository.flush();
        });
        paymentIds.clear();
        orderIds.clear();
        customerIds.clear();
    }

    @Test
    void shouldCreatePaymentAttemptWithPersistedOrderAmount() {
        UUID orderId = persistOrder();
        var request = buildCreatePaymentRequest();

        var result = paymentService.createPaymentAttempt(orderId, request);
        paymentIds.add(result.id());

        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.amount()).isEqualByComparingTo("247.00");
        assertThat(result.provider()).isEqualTo(PaymentProvider.STRIPE);
        assertThat(result.idempotencyKey()).isEqualTo(request.idempotencyKey());
        var persisted = readPayment(result.id());
        assertThat(persisted.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(persisted.getAmount()).isEqualByComparingTo("247.00");
        assertThat(persisted.getOrderId()).isEqualTo(orderId);
    }

    @Test
    void shouldReuseExistingPaymentForRepeatedIdempotencyKey() {
        UUID orderId = persistOrder();
        var request = buildCreatePaymentRequest();

        var first = paymentService.createPaymentAttempt(orderId, request);
        paymentIds.add(first.id());

        var repeated = paymentService.createPaymentAttempt(orderId, request);

        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(paymentRepository.findByOrderId(orderId)).hasSize(1);
    }

    @Test
    void shouldRejectReusingIdempotencyKeyForAnotherOrder() {
        UUID firstOrder = persistOrder();
        UUID otherOrder = persistOrder();
        var paymentRequest = buildCreatePaymentRequest();

        var paymentResponse = paymentService.createPaymentAttempt(firstOrder, paymentRequest);
        paymentIds.add(paymentResponse.id());

        assertThatThrownBy(() -> paymentService.createPaymentAttempt(otherOrder, paymentRequest))
                .isInstanceOf(InvalidPaymentStateException.class);

        assertThat(paymentRepository.findByOrderId(otherOrder)).isEmpty();
        assertThat(readPayment(paymentResponse.id()).getOrderId()).isEqualTo(firstOrder);
    }

    @Test
    void shouldListPaymentsForExistingOrder() {
        UUID orderId = persistOrder();
        UUID first = persistPayment(orderId, PaymentStatus.CREATED);
        UUID second = persistPayment(orderId, PaymentStatus.FAILED);
        persistPayment(persistOrder(), PaymentStatus.CREATED);

        assertThat(paymentService.getAllPayments(orderId)).extracting(PaymentAttemptResponse::id)
                .containsExactlyInAnyOrder(first, second);
    }

    @Test
    void shouldReturnEmptyListForOrderWithoutPayments() {
        assertThat(paymentService.getAllPayments(persistOrder())).isEmpty();
    }

    @Test
    void shouldGetPersistedPaymentById() {
        UUID orderId = persistOrder();
        UUID paymentId = persistPayment(orderId, PaymentStatus.CREATED);

        var result = paymentService.getPaymentAttempt(orderId, paymentId);

        assertThat(result.id()).isEqualTo(paymentId);
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.amount()).isEqualByComparingTo("247.00");
        assertThat(result.status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(result.provider()).isEqualTo(PaymentProvider.STRIPE);
    }

    @Test
    void shouldRejectPaymentBelongingToAnotherOrder() {
        UUID paymentId = persistPayment(persistOrder(), PaymentStatus.CREATED);
        UUID otherOrder = persistOrder();

        assertThatThrownBy(() -> paymentService.getPaymentAttempt(otherOrder, paymentId))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void shouldRejectOperationsForMissingPayment() {
        UUID orderId = persistOrder();
        UUID missingId = UUID.randomUUID();

        assertThatThrownBy(() -> paymentService.getPaymentAttempt(orderId, missingId))
                .isInstanceOf(PaymentNotFoundException.class);
        assertThatThrownBy(() -> paymentService.startProcessing(orderId, missingId))
                .isInstanceOf(PaymentNotFoundException.class);
        assertThatThrownBy(() -> paymentService.markAsSucceeded(orderId, missingId, "ref"))
                .isInstanceOf(PaymentNotFoundException.class);
        assertThatThrownBy(() -> paymentService.markAsFailed(orderId, missingId, 402, "Declined"))
                .isInstanceOf(PaymentNotFoundException.class);
        assertThatThrownBy(() -> paymentService.markAsCancelled(orderId, missingId))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void shouldRejectCreatingPaymentForMissingOrder() {
        assertThatThrownBy(() -> paymentService.createPaymentAttempt(UUID.randomUUID(), buildCreatePaymentRequest()))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "PENDING_PAYMENT")
    void shouldRejectCreatingPaymentForNonPendingOrder(OrderStatus status) {
        UUID orderId = persistOrder();
        changeOrderStatus(orderId, status);

        assertThatThrownBy(() -> paymentService.createPaymentAttempt(orderId, buildCreatePaymentRequest()))
                .isInstanceOf(InvalidOrderStateException.class);
        assertThat(paymentRepository.findByOrderId(orderId)).isEmpty();
    }

    @Test
    void shouldStartProcessingAndPersistPaymentStatus() {
        UUID orderId = persistOrder();
        UUID paymentId = persistPayment(orderId, PaymentStatus.CREATED);

        assertThat(paymentService.startProcessing(orderId, paymentId).status()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(readPayment(paymentId).getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    void shouldMarkPaymentSucceededAndPersistOrderAsPaid() {
        UUID orderId = persistOrder();
        UUID paymentId = persistPayment(orderId, PaymentStatus.PROCESSING);

        assertThat(paymentService.markAsSucceeded(orderId, paymentId, "ref-123").status())
                .isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(readPayment(paymentId).getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(readPayment(paymentId).getProviderRef()).isEqualTo("ref-123");
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void shouldMarkPaymentFailedAndPersistFailureDetails() {
        UUID orderId = persistOrder();
        UUID paymentId = persistPayment(orderId, PaymentStatus.PROCESSING);

        assertThat(paymentService.markAsFailed(orderId, paymentId, 402, "Declined").status())
                .isEqualTo(PaymentStatus.FAILED);
        var persisted = readPayment(paymentId);
        assertThat(persisted.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(persisted.getFailureCode()).isEqualTo(402);
        assertThat(persisted.getFailureMessage()).isEqualTo("Declined");
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = { "CREATED", "PROCESSING" })
    void shouldCancelPaymentWithoutChangingOrderStatus(PaymentStatus status) {
        UUID orderId = persistOrder();
        UUID paymentId = persistPayment(orderId, status);

        assertThat(paymentService.markAsCancelled(orderId, paymentId).status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(readPayment(paymentId).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = { "FAILED", "CANCELLED", "SUCCEEDED" })
    void shouldRejectInvalidPaymentTransitionsWithoutChangingPersistedState(PaymentStatus status) {
        UUID orderId = persistOrder();
        UUID paymentId = persistPayment(orderId, status);

        assertThatThrownBy(() -> paymentService.startProcessing(orderId, paymentId))
                .isInstanceOf(InvalidPaymentStateException.class);
        assertThatThrownBy(() -> paymentService.markAsSucceeded(orderId, paymentId, "ref"))
                .isInstanceOf(InvalidPaymentStateException.class);
        assertThatThrownBy(() -> paymentService.markAsFailed(orderId, paymentId, 402, "Declined"))
                .isInstanceOf(InvalidPaymentStateException.class);
        assertThatThrownBy(() -> paymentService.markAsCancelled(orderId, paymentId))
                .isInstanceOf(InvalidPaymentStateException.class);
        assertThat(readPayment(paymentId).getStatus()).isEqualTo(status);
        assertThat(readPayment(paymentId).getProviderRef()).isNull();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void shouldRollBackPaymentSuccessWhenOrderCannotBeMarkedPaid() {
        UUID orderId = persistOrder();
        UUID paymentId = persistPayment(orderId, PaymentStatus.PROCESSING);
        changeOrderStatus(orderId, OrderStatus.CANCELLED);

        assertThatThrownBy(() -> paymentService.markAsSucceeded(orderId, paymentId, "ref-123"))
                .isInstanceOf(InvalidOrderStateException.class);

        assertThat(readPayment(paymentId).getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(readPayment(paymentId).getProviderRef()).isNull();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void shouldRollBackPaymentCreationWhenCallerTransactionFails() {
        UUID orderId = persistOrder();
        var request = buildCreatePaymentRequest();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            var result = paymentService.createPaymentAttempt(orderId, request);
            paymentIds.add(result.id());
            entityManager.flush();
            throw new IllegalStateException("Caller failed.");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Caller failed.");

        assertThat(paymentRepository.findByIdempotencyKey(request.idempotencyKey())).isEmpty();
        assertThat(paymentRepository.findByOrderId(orderId)).isEmpty();
    }

    @Test
    void shouldRollBackPaymentAndOrderUpdatesWhenCallerTransactionFails() {
        UUID orderId = persistOrder();
        UUID paymentId = persistPayment(orderId, PaymentStatus.PROCESSING);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            paymentService.markAsSucceeded(orderId, paymentId, "ref-123");
            entityManager.flush();
            entityManager.clear();
            assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.SUCCEEDED);
            assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
            throw new IllegalStateException("Caller failed.");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Caller failed.");

        assertThat(readPayment(paymentId).getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(readPayment(paymentId).getProviderRef()).isNull();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    private CreatePaymentAttemptRequest buildCreatePaymentRequest() {
        return new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, UUID.randomUUID().toString());
    }

    private PaymentAttemptEntity readPayment(UUID id) {
        return transactionTemplate.execute(status -> paymentRepository.findById(id).orElseThrow());
    }

    private void changeOrderStatus(UUID id, OrderStatus target) {
        transactionTemplate.executeWithoutResult(status -> {
            OrderEntity order = orderRepository.findById(id).orElseThrow();
            switch (target) {
                case PAID -> order.markAsPaid();
                case CANCELLED -> order.cancelOrder();
                case EXPIRED -> order.expire();
                case PAYMENT_IN_PROGRESS -> order.paymentInProgress();
                case REFUNDED -> {
                    order.markAsPaid();
                    order.refund();
                }
                default -> throw new IllegalArgumentException("Unsupported fixture status");
            }
            orderRepository.saveAndFlush(order);
        });
    }

    private UUID persistOrder() {
        return transactionTemplate.execute(status -> {

            UUID customerId = customerRepository.saveAndFlush(
                    new CustomerEntity(UUID.randomUUID() + "@example.com", "Test Customer")).getId();

            customerIds.add(customerId);

            UUID orderId = orderRepository.saveAndFlush(OrderEntity.builder()
                    .customerId(customerId)
                    .addItem(OrderItemEntity.builder()
                            .sku("SKU-123")
                            .description("Test item")
                            .unitPrice(new BigDecimal("123.50"))
                            .quantity(2L)
                            .build())
                    .build()).getId();

            orderIds.add(orderId);

            return orderId;
        });
    }

    private UUID persistPayment(UUID orderId, PaymentStatus paymentStatus) {
        return transactionTemplate.execute(status -> {

            UUID id = paymentRepository.saveAndFlush(PaymentAttemptEntity.builder()
                    .orderId(orderId).amount(new BigDecimal("247.00"))
                    .provider(PaymentProvider.STRIPE).status(paymentStatus).build()).getId();

            paymentIds.add(id);

            return id;
        });
    }
}
