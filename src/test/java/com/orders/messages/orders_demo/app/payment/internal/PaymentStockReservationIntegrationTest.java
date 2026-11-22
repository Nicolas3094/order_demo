package com.orders.messages.orders_demo.app.payment.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.order.internal.OrderEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderItemEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderRepository;
import com.orders.messages.orders_demo.app.order.internal.OrderStatus;
import com.orders.messages.orders_demo.app.payment.api.CreatePaymentAttemptRequest;
import com.orders.messages.orders_demo.app.product.internal.ProductEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductRepository;
import com.orders.messages.orders_demo.app.product.internal.exceptions.InvalidProductException;

class PaymentStockReservationIntegrationTest extends PostgresContainer {
    @Autowired
    private PaymentAttemptService paymentService;
    @Autowired
    private PaymentAttemptRepository paymentRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<UUID> orderIds = new ArrayList<>();
    private final List<UUID> customerIds = new ArrayList<>();
    private final List<UUID> productIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            orderIds.forEach(id -> paymentRepository.deleteAll(paymentRepository.findByOrderId(id)));
            paymentRepository.flush();
            orderIds.forEach(orderRepository::deleteById);
            orderRepository.flush();
            customerIds.forEach(customerRepository::deleteById);
            customerRepository.flush();
            productIds.forEach(productRepository::deleteById);
            productRepository.flush();
        });
    }

    @Test
    void shouldReserveStockOnceWhenPaymentCreationIsRetriedWithSameKey() {
        UUID productId = persistProduct("SKU-A", 5L);
        UUID orderId = persistOrder("SKU-A", 2L);
        var request = request();

        var first = paymentService.createPaymentAttempt(orderId, request);
        var repeated = paymentService.createPaymentAttempt(orderId, request);

        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(stock(productId)).isEqualTo(3L);
        assertThat(paymentRepository.findByOrderId(orderId)).hasSize(1);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
    }

    @Test
    void shouldRejectCheckoutWhenStockWasConsumedAfterAddingItems() {
        UUID productId = persistProduct("SKU-A", 1L);
        UUID orderId = persistOrder("SKU-A", 2L);

        assertThatThrownBy(() -> paymentService.createPaymentAttempt(orderId, request()))
                .isInstanceOf(InvalidProductException.class);

        assertThat(stock(productId)).isEqualTo(1L);
        assertThat(paymentRepository.findByOrderId(orderId)).isEmpty();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void shouldRollBackAllReservationsWhenOneProductHasInsufficientStock() {
        UUID firstProduct = persistProduct("SKU-A", 5L);
        UUID secondProduct = persistProduct("SKU-B", 1L);
        UUID orderId = persistOrder("SKU-A", 2L);
        transactionTemplate.executeWithoutResult(status -> {
            var order = orderRepository.findById(orderId).orElseThrow();
            order.addItem(item("SKU-B", 2L));
            orderRepository.saveAndFlush(order);
        });

        assertThatThrownBy(() -> paymentService.createPaymentAttempt(orderId, request()))
                .isInstanceOf(InvalidProductException.class);

        assertThat(stock(firstProduct)).isEqualTo(5L);
        assertThat(stock(secondProduct)).isEqualTo(1L);
        assertThat(paymentRepository.findByOrderId(orderId)).isEmpty();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void shouldRollBackStockReservationWhenCallerFails() {
        UUID productId = persistProduct("SKU-A", 5L);
        UUID orderId = persistOrder("SKU-A", 2L);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            paymentService.createPaymentAttempt(orderId, request());
            entityManager.flush();
            entityManager.clear();
            assertThat(stock(productId)).isEqualTo(3L);
            throw new IllegalStateException("Caller failed");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Caller failed");

        assertThat(stock(productId)).isEqualTo(5L);
        assertThat(paymentRepository.findByOrderId(orderId)).isEmpty();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void shouldReleaseReservationOnFailure() {
        // Existing active attempt with two units already reserved: available stock is three.
        UUID productId = persistProduct("SKU-A", 3L);
        UUID orderId = persistOrder("SKU-A", 2L);
        UUID paymentId = persistReservedPayment(orderId);

        paymentService.markAsFailed(orderId, paymentId, 402, "Declined");
        paymentService.markAsFailed(orderId, paymentId, 402, "Declined");

        assertThat(stock(productId)).isEqualTo(5L);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void shouldReleaseReservationOnCancellation() {
        // Existing active attempt with two units already reserved: available stock is three.
        UUID productId = persistProduct("SKU-A", 3L);
        UUID orderId = persistOrder("SKU-A", 2L);
        UUID paymentId = persistReservedPayment(orderId);

        paymentService.markAsCancelled(orderId, paymentId);

        assertThat(stock(productId)).isEqualTo(5L);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void shouldKeepReservationOnSuccess() {
        // Existing active attempt with two units already reserved: available stock is three.
        UUID productId = persistProduct("SKU-A", 3L);
        UUID orderId = persistOrder("SKU-A", 2L);
        UUID paymentId = persistReservedPayment(orderId);

        paymentService.markAsSucceeded(orderId, paymentId, "ref-stock");
        paymentService.markAsSucceeded(orderId, paymentId, "ref-stock");

        assertThat(stock(productId)).isEqualTo(3L);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    private UUID persistReservedPayment(UUID orderId) {
        return transactionTemplate.execute(status -> {
            var order = orderRepository.findById(orderId).orElseThrow();
            order.paymentInProgress();
            orderRepository.saveAndFlush(order);
            return paymentRepository.saveAndFlush(PaymentAttemptEntity.builder()
                    .orderId(orderId).amount(new BigDecimal("200.00"))
                    .status(PaymentStatus.PROCESSING).provider(PaymentProvider.STRIPE).build()).getId();
        });
    }

    private UUID persistProduct(String sku, long stock) {
        UUID id = productRepository.saveAndFlush(ProductEntity.builder().sku(sku).name("Product")
                .price(new BigDecimal("100.00")).quantity(stock).build()).getId();
        productIds.add(id);
        return id;
    }

    private UUID persistOrder(String sku, long quantity) {
        UUID customerId = customerRepository.saveAndFlush(new CustomerEntity(UUID.randomUUID() + "@example.com", "Customer")).getId();
        customerIds.add(customerId);
        UUID id = orderRepository.saveAndFlush(OrderEntity.builder().customerId(customerId)
                .addItem(item(sku, quantity)).build()).getId();
        orderIds.add(id);
        return id;
    }

    private OrderItemEntity item(String sku, long quantity) {
        return OrderItemEntity.builder().sku(sku).unitPrice(new BigDecimal("100.00")).quantity(quantity).build();
    }

    private CreatePaymentAttemptRequest request() {
        return new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, UUID.randomUUID().toString());
    }

    private long stock(UUID id) {
        return productRepository.findById(id).orElseThrow().getQuantity();
    }
}
