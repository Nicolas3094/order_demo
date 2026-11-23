package com.orders.messages.orders_demo.app.payment.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.product.internal.ProductEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductRepository;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.order.internal.*;
import com.orders.messages.orders_demo.app.order.internal.exceptions.*;
import com.orders.messages.orders_demo.app.payment.api.*;
import com.orders.messages.orders_demo.app.payment.internal.exceptions.*;

class PaymentServiceIntegrationTest extends PostgresContainer {

    @Autowired
    private OrderItemService orderItemService;
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
    private final List<UUID> reservationProductIds = new ArrayList<>();
    @Autowired
    private ProductRepository productRepository;

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            paymentIds.forEach(paymentRepository::deleteById);
            paymentRepository.flush();
            orderIds.forEach(orderRepository::deleteById);
            orderRepository.flush();
            customerIds.forEach(customerRepository::deleteById);
            customerRepository.flush();
            reservationProductIds.forEach(productRepository::deleteById);
            productRepository.flush();
        });
        paymentIds.clear();
        orderIds.clear();
        customerIds.clear();
        reservationProductIds.clear();
    }

    @Test
    void shouldCreatePaymentAttemptWithPersistedOrderAmount() {
        UUID orderId = persistOrder();
        var paymentRequest = buildCreatePaymentRequest();

        var result = paymentService.createPaymentAttempt(orderId, paymentRequest);
        paymentIds.add(result.id());

        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.amount()).isEqualByComparingTo("247.00");
        assertThat(result.provider()).isEqualTo(PaymentProvider.STRIPE);
        assertThat(result.idempotencyKey()).isEqualTo(paymentRequest.idempotencyKey());
        var persistedPayment = readPayment(result.id());
        assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(persistedPayment.getAmount()).isEqualByComparingTo("247.00");
        assertThat(persistedPayment.getOrderId()).isEqualTo(orderId);
        var persistedOrder = readOrder(orderId);
        assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
    }

    @Test
    void shouldReuseExistingPaymentForRepeatedIdempotencyKey() {
        UUID orderId = persistOrder();
        var paymentRequest = buildCreatePaymentRequest();

        var first = paymentService.createPaymentAttempt(orderId, paymentRequest);
        paymentIds.add(first.id());
        var repeated = paymentService.createPaymentAttempt(orderId, paymentRequest);

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
        changeOrderStatus(orderId, OrderStatus.PAYMENT_IN_PROGRESS);
        UUID paymentId = persistPayment(orderId, PaymentStatus.CREATED);

        assertThat(paymentService.startProcessing(orderId, paymentId).status()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(readPayment(paymentId).getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    void shouldMarkPaymentSucceededAndPersistOrderAsPaid() {
        UUID orderId = persistOrder();
        changeOrderStatus(orderId, OrderStatus.PAYMENT_IN_PROGRESS);
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
        changeOrderStatus(orderId, OrderStatus.PAYMENT_IN_PROGRESS);
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
    void shouldCancelPaymentAndReturnOrderToPending(PaymentStatus status) {
        UUID orderId = persistOrder();
        changeOrderStatus(orderId, OrderStatus.PAYMENT_IN_PROGRESS);
        UUID paymentId = persistPayment(orderId, status);

        assertThat(paymentService.markAsCancelled(orderId, paymentId).status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(readPayment(paymentId).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = { "FAILED", "CANCELLED", "SUCCEEDED" })
    void shouldRejectInvalidPaymentTransitionsWithoutChangingPersistedState(PaymentStatus status) {
        UUID orderId = persistOrder();
        changeOrderStatus(orderId, OrderStatus.PAYMENT_IN_PROGRESS);
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
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
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
        transactionTemplate.executeWithoutResult(status -> {
            OrderEntity persistedOrder = orderRepository.findById(orderId).orElseThrow();

            assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        });
    }

    @Test
    void shouldRollBackPaymentAndOrderUpdatesWhenCallerTransactionFails() {
        UUID orderId = persistOrder();
        changeOrderStatus(orderId, OrderStatus.PAYMENT_IN_PROGRESS);
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
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = { "CREATED", "PROCESSING" })
    void shouldRollBackCancellationWhenOrderCannotReturnToPending(PaymentStatus initialStatus) {
        UUID orderId = persistOrder();
        UUID paymentId = persistPayment(orderId, initialStatus);
        changeOrderStatus(orderId, OrderStatus.CANCELLED);

        assertThatThrownBy(() -> paymentService.markAsCancelled(orderId, paymentId))
                .isInstanceOf(InvalidOrderStateException.class);

        assertThat(readPayment(paymentId).getStatus()).isEqualTo(initialStatus);
        assertThat(readOrder(orderId).getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void shouldRollBackPaymentAndOrderCancellationWhenCallerFails() {
        UUID orderId = persistOrder();
        changeOrderStatus(orderId, OrderStatus.PAYMENT_IN_PROGRESS);
        UUID paymentId = persistPayment(orderId, PaymentStatus.PROCESSING);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            paymentService.markAsCancelled(orderId, paymentId);
            entityManager.flush();
            entityManager.clear();
            assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.CANCELLED);
            assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PENDING_PAYMENT);
            throw new IllegalStateException("Caller failed.");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Caller failed.");

        assertThat(readPayment(paymentId).getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(readOrder(orderId).getStatus()).isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
    }

    @Test
    void shouldAcceptRepeatedSuccessNotificationWithoutChangingPaymentOrOrderAgain() {
        UUID orderId = persistOrder();
        var created = paymentService.createPaymentAttempt(orderId, buildCreatePaymentRequest());
        paymentIds.add(created.id());
        paymentService.startProcessing(orderId, created.id());
        var first = paymentService.markAsSucceeded(orderId, created.id(), "ref-repeat");
        var version = readOrder(orderId).getVersion();
        var firstSuccessUpdatedAt = readPayment(created.id()).getUpdatedAt();

        var repeated = paymentService.markAsSucceeded(orderId, created.id(), "ref-repeat");

        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(repeated.status()).isEqualTo(first.status());
        assertThat(repeated.amount()).isEqualByComparingTo(first.amount());
        assertThat(readPayment(created.id()).getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(readPayment(created.id()).getUpdatedAt()).isEqualTo(firstSuccessUpdatedAt);
        assertThat(readOrder(orderId).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(readOrder(orderId).getVersion()).isEqualTo(version);
        assertThat(paymentRepository.findByOrderId(orderId)).hasSize(1);
    }

    @Test
    void shouldAcceptRepeatedFailureNotificationWithoutChangingPaymentOrOrderAgain() {
        UUID orderId = persistOrder();
        var created = paymentService.createPaymentAttempt(orderId, buildCreatePaymentRequest());
        paymentIds.add(created.id());
        paymentService.startProcessing(orderId, created.id());
        var first = paymentService.markAsFailed(orderId, created.id(), 402, "Declined");
        var version = readOrder(orderId).getVersion();
        var firstFailureUpdatedAt = readPayment(created.id()).getUpdatedAt();

        var repeated = paymentService.markAsFailed(orderId, created.id(), 402, "Declined");

        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(repeated.status()).isEqualTo(first.status());
        assertThat(repeated.amount()).isEqualByComparingTo(first.amount());
        assertThat(readPayment(created.id()).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(readPayment(created.id()).getUpdatedAt()).isEqualTo(firstFailureUpdatedAt);
        assertThat(readOrder(orderId).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(readOrder(orderId).getVersion()).isEqualTo(version);
        assertThat(paymentRepository.findByOrderId(orderId)).hasSize(1);
    }

    @Test
    void shouldRejectStaleItemEditWhenPaymentStartsInAnotherTransaction() throws Exception {
        UUID orderId = persistOrder();
        var editReady = new java.util.concurrent.CountDownLatch(1);
        var paymentCreated = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();

        try {
            var edit = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                var order = orderRepository.findById(orderId).orElseThrow();
                UUID itemId = order.getItems().get(0).getId();
                editReady.countDown();
                try {
                    if (!paymentCreated.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Payment creation timed out");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                orderItemService.changeUnitPrice(orderId, itemId, new BigDecimal("200.00"));
                entityManager.flush();
            }));
            assertThat(editReady.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var payment = paymentService.createPaymentAttempt(orderId, buildCreatePaymentRequest());
            paymentIds.add(payment.id());
            paymentCreated.countDown();

            assertThatThrownBy(() -> edit.get(10, java.util.concurrent.TimeUnit.SECONDS))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class)
                    .hasCauseInstanceOf(jakarta.persistence.OptimisticLockException.class);

            transactionTemplate.executeWithoutResult(status -> {
                var order = orderRepository.findById(orderId).orElseThrow();
                var persistedPayment = paymentRepository.findById(payment.id()).orElseThrow();
                assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
                assertThat(order.getAmountTotal()).isEqualByComparingTo("247.00");
                assertThat(order.getItems().get(0).getUnitPrice()).isEqualByComparingTo("123.50");
                assertThat(persistedPayment.getAmount()).isEqualByComparingTo(order.getAmountTotal());
            });
        } finally {
            paymentCreated.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @Test
    void shouldRollBackPaymentFailureWhenOrderCannotReturnToPending() {
        UUID orderId = persistOrder();
        UUID paymentId = persistPayment(orderId, PaymentStatus.PROCESSING);
        changeOrderStatus(orderId, OrderStatus.CANCELLED);

        assertThatThrownBy(() -> paymentService.markAsFailed(orderId, paymentId, 402, "Declined"))
                .isInstanceOf(InvalidOrderStateException.class);

        var processingPayment = readPayment(paymentId);
        assertThat(processingPayment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(processingPayment.getFailureCode()).isNull();
        assertThat(processingPayment.getFailureMessage()).isNull();
        assertThat(readOrder(orderId).getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    private CreatePaymentAttemptRequest buildCreatePaymentRequest() {
        return new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, UUID.randomUUID().toString());
    }

    private OrderEntity readOrder(UUID id) {
        entityManager.clear();
        return transactionTemplate.execute(status -> orderRepository.findById(id).orElseThrow());
    }

    private PaymentAttemptEntity readPayment(UUID id) {
        entityManager.clear();
        return transactionTemplate.execute(status -> paymentRepository.findById(id).orElseThrow());
    }

    private void changeOrderStatus(UUID id, OrderStatus target) {
        transactionTemplate.executeWithoutResult(status -> {
            OrderEntity order = orderRepository.findById(id).orElseThrow();
            switch (target) {
                case PAID -> {
                    order.paymentInProgress();
                    order.markAsPaid();
                }
                case CANCELLED -> order.cancelOrder();
                case EXPIRED -> order.expire();
                case PAYMENT_IN_PROGRESS -> order.paymentInProgress();
                case REFUNDED -> {
                    order.paymentInProgress();
                    order.markAsPaid();
                    order.refund();
                }
                default -> throw new IllegalArgumentException("Unsupported fixture status");
            }
            orderRepository.saveAndFlush(order);
        });
    }

    @Test
    void shouldRejectPaymentForEmptyOrderWithoutPersistedChanges() {
        UUID orderId = persistOrder();
        transactionTemplate.executeWithoutResult(status -> {
            var order = orderRepository.findById(orderId).orElseThrow();
            var item = order.getItems().get(0);
            order.removeItem(item);
            orderRepository.saveAndFlush(order);
        });
        var initialVersion = orderRepository.findById(orderId).orElseThrow().getVersion();
        String key = UUID.randomUUID().toString();
        var request = new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, key);

        assertThatThrownBy(() -> paymentService.createPaymentAttempt(orderId, request))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessage("Order must contain items and have a positive total to start payment.");

        transactionTemplate.executeWithoutResult(status -> {
            var order = orderRepository.findById(orderId).orElseThrow();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(order.getVersion()).isEqualTo(initialVersion);
            assertThat(order.getItems()).isEmpty();
            assertThat(order.getAmountTotal()).isEqualByComparingTo("0");
            assertThat(paymentRepository.findByOrderId(orderId)).isEmpty();
            assertThat(paymentRepository.findByIdempotencyKey(key)).isEmpty();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = { "0.00", "-1.00" })
    void shouldRejectPaymentForNonPositiveOrderTotalWithoutPersistedChanges(String total) {
        UUID orderId = persistOrder();
        // Simulate inconsistent persisted data while retaining the order item.
        transactionTemplate.executeWithoutResult(status -> entityManager
                .createNativeQuery("UPDATE orders SET amount_total = :total WHERE id = :id")
                .setParameter("total", new BigDecimal(total)).setParameter("id", orderId).executeUpdate());
        var initialVersion = orderRepository.findById(orderId).orElseThrow().getVersion();
        String key = UUID.randomUUID().toString();
        var request = new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, key);

        assertThatThrownBy(() -> paymentService.createPaymentAttempt(orderId, request))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessage("Order must contain items and have a positive total to start payment.");

        transactionTemplate.executeWithoutResult(status -> {
            var order = orderRepository.findById(orderId).orElseThrow();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(order.getVersion()).isEqualTo(initialVersion);
            assertThat(order.getItems()).hasSize(1);
            assertThat(order.getAmountTotal()).isEqualByComparingTo(total);
            assertThat(paymentRepository.findByOrderId(orderId)).isEmpty();
            assertThat(paymentRepository.findByIdempotencyKey(key)).isEmpty();
        });
    }

    private UUID persistOrder() {
        return transactionTemplate.execute(status -> {
            productRepository.findBySku("SKU-123").orElseGet(() -> {
                ProductEntity persistedProduct = productRepository.saveAndFlush(ProductEntity.builder()
                        .sku("SKU-123").name("Payment product").description("Payment fixture")
                        .price(new BigDecimal("123.50")).quantity(100L).build());
                reservationProductIds.add(persistedProduct.getId());
                return persistedProduct;
            });

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
