package com.orders.messages.orders_demo.app.order.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.order.api.OrderInternalApi;
import com.orders.messages.orders_demo.app.payment.api.CreatePaymentAttemptRequest;
import com.orders.messages.orders_demo.app.payment.internal.PaymentAttemptEntity;
import com.orders.messages.orders_demo.app.payment.internal.PaymentAttemptRepository;
import com.orders.messages.orders_demo.app.payment.internal.PaymentAttemptService;
import com.orders.messages.orders_demo.app.payment.internal.PaymentProvider;
import com.orders.messages.orders_demo.app.payment.internal.PaymentStatus;
import com.orders.messages.orders_demo.app.product.internal.ProductEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductRepository;

class OrderStateConcurrencyIntegrationTest extends PostgresContainer {
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderInternalApi orderInternalApi;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private PaymentAttemptService paymentService;
    @Autowired
    private PaymentAttemptRepository paymentRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<UUID> orderIds = new ArrayList<>();
    private final List<UUID> customerIds = new ArrayList<>();
    private final List<UUID> productIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            orderIds.forEach(orderId -> paymentRepository.deleteAll(paymentRepository.findByOrderId(orderId)));
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
    void shouldRejectStaleExpirationAfterOrderCancellationCommits() throws Exception {
        UUID productId = persistProduct();
        UUID orderId = persistOrder(productId, OrderStatus.PENDING_PAYMENT);
        Long initialVersion = orderRepository.findById(orderId).orElseThrow().getVersion();

        Throwable expirationFailure = runWithStaleOrderSnapshot(orderId,
                () -> orderService.expireOrder(orderId),
                () -> orderService.cancelOrder(orderId));

        assertOptimisticLockFailure(expirationFailure);
        assertPersistedOrder(orderId, OrderStatus.CANCELLED, initialVersion + 1);
        assertThat(productRepository.findById(productId).orElseThrow().getQuantity()).isEqualTo(5L);
        assertThat(paymentRepository.findByOrderId(orderId)).isEmpty();
    }

    @Test
    void shouldRejectStaleCancellationAfterOrderExpirationCommits() throws Exception {
        UUID productId = persistProduct();
        UUID orderId = persistOrder(productId, OrderStatus.PENDING_PAYMENT);
        Long initialVersion = orderRepository.findById(orderId).orElseThrow().getVersion();

        Throwable cancellationFailure = runWithStaleOrderSnapshot(orderId,
                () -> orderService.cancelOrder(orderId),
                () -> orderService.expireOrder(orderId));

        assertOptimisticLockFailure(cancellationFailure);
        assertPersistedOrder(orderId, OrderStatus.EXPIRED, initialVersion + 1);
        assertThat(productRepository.findById(productId).orElseThrow().getQuantity()).isEqualTo(5L);
        assertThat(paymentRepository.findByOrderId(orderId)).isEmpty();
    }

    @Test
    void shouldRejectStaleCancellationAfterCheckoutCommits() throws Exception {
        UUID productId = persistProduct();
        UUID orderId = persistOrder(productId, OrderStatus.PENDING_PAYMENT);
        Long initialVersion = orderRepository.findById(orderId).orElseThrow().getVersion();
        var createPaymentRequest = createPaymentRequest();

        Throwable cancellationFailure = runWithStaleOrderSnapshot(orderId,
                () -> orderService.cancelOrder(orderId),
                () -> paymentService.createPaymentAttempt(orderId, createPaymentRequest));

        assertOptimisticLockFailure(cancellationFailure);
        assertPersistedOrder(orderId, OrderStatus.PAYMENT_IN_PROGRESS, initialVersion + 1);
        assertThat(productRepository.findById(productId).orElseThrow().getQuantity()).isEqualTo(3L);
        var persistedPayments = paymentRepository.findByOrderId(orderId);
        assertThat(persistedPayments).hasSize(1);
        assertThat(persistedPayments).extracting(PaymentAttemptEntity::getStatus).containsExactly(PaymentStatus.CREATED);
        assertThat(persistedPayments.get(0).getAmount()).isEqualByComparingTo("200.00");
        assertThat(persistedPayments.get(0).getIdempotencyKey()).isEqualTo(createPaymentRequest.idempotencyKey());
    }

    @Test
    void shouldRollBackCheckoutReservationAndPaymentWhenOrderWasCancelledConcurrently() throws Exception {
        UUID productId = persistProduct();
        UUID orderId = persistOrder(productId, OrderStatus.PENDING_PAYMENT);
        Long initialVersion = orderRepository.findById(orderId).orElseThrow().getVersion();
        var createPaymentRequest = createPaymentRequest();

        Throwable checkoutFailure = runWithStaleOrderSnapshot(orderId,
                () -> paymentService.createPaymentAttempt(orderId, createPaymentRequest),
                () -> orderService.cancelOrder(orderId));

        assertOptimisticLockFailure(checkoutFailure);
        assertPersistedOrder(orderId, OrderStatus.CANCELLED, initialVersion + 1);
        assertThat(productRepository.findById(productId).orElseThrow().getQuantity()).isEqualTo(5L);
        assertThat(paymentRepository.findByOrderId(orderId)).isEmpty();
        assertThat(paymentRepository.findByIdempotencyKey(createPaymentRequest.idempotencyKey())).isEmpty();
    }

    @Test
    void shouldRejectStalePaidTransitionAfterOrderReturnsToPending() throws Exception {
        UUID productId = persistProduct();
        UUID orderId = persistOrder(productId, OrderStatus.PAYMENT_IN_PROGRESS);
        Long initialVersion = orderRepository.findById(orderId).orElseThrow().getVersion();

        Throwable paidTransitionFailure = runWithStaleOrderSnapshot(orderId,
                () -> orderInternalApi.markAsPaid(orderId),
                () -> orderInternalApi.markAsPending(orderId));

        assertOptimisticLockFailure(paidTransitionFailure);
        assertPersistedOrder(orderId, OrderStatus.PENDING_PAYMENT, initialVersion + 1);
    }

    @Test
    void shouldRejectStalePendingTransitionAfterOrderIsMarkedPaid() throws Exception {
        UUID productId = persistProduct();
        UUID orderId = persistOrder(productId, OrderStatus.PAYMENT_IN_PROGRESS);
        Long initialVersion = orderRepository.findById(orderId).orElseThrow().getVersion();

        Throwable pendingTransitionFailure = runWithStaleOrderSnapshot(orderId,
                () -> orderInternalApi.markAsPending(orderId),
                () -> orderInternalApi.markAsPaid(orderId));

        assertOptimisticLockFailure(pendingTransitionFailure);
        assertPersistedOrder(orderId, OrderStatus.PAID, initialVersion + 1);
    }

    @Test
    void shouldRejectSecondConcurrentRefundOfSameOrder() throws Exception {
        UUID productId = persistProduct();
        UUID orderId = persistOrder(productId, OrderStatus.PAID);
        Long initialVersion = orderRepository.findById(orderId).orElseThrow().getVersion();

        Throwable repeatedRefundFailure = runWithStaleOrderSnapshot(orderId,
                () -> orderService.refundOrder(orderId),
                () -> orderService.refundOrder(orderId));

        assertOptimisticLockFailure(repeatedRefundFailure);
        assertPersistedOrder(orderId, OrderStatus.REFUNDED, initialVersion + 1);
        assertThat(productRepository.findById(productId).orElseThrow().getQuantity()).isEqualTo(5L);
    }

    private Throwable runWithStaleOrderSnapshot(UUID orderId, Runnable staleOrderChange, Runnable committedOrderChange)
            throws Exception {
        var orderSnapshotRead = new CountDownLatch(1);
        var orderChangeCommitted = new CountDownLatch(1);
        var orderExecutor = Executors.newSingleThreadExecutor();

        try {
            var staleOrderChangeFuture = orderExecutor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                // Esta transacción conserva la orden con su versión anterior en memoria.
                orderRepository.findById(orderId).orElseThrow().getItems().size();
                orderSnapshotRead.countDown();
                awaitOrderSignal(orderChangeCommitted, "The competing order change must commit first");

                staleOrderChange.run();
                // Fuerza la comprobación de @Version antes de terminar el bloque.
                entityManager.flush();
            }));
            awaitOrderSignal(orderSnapshotRead, "The original order must be read before the competing change");

            committedOrderChange.run();
            orderChangeCommitted.countDown();
            return catchThrowable(() -> staleOrderChangeFuture.get(20, TimeUnit.SECONDS));
        } finally {
            orderChangeCommitted.countDown();
            orderExecutor.shutdownNow();
            assertThat(orderExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void awaitOrderSignal(CountDownLatch orderSignal, String description) {
        try {
            assertThat(orderSignal.await(10, TimeUnit.SECONDS)).as(description).isTrue();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent order change interrupted", interruptedException);
        }
    }

    private void assertOptimisticLockFailure(Throwable staleOrderChangeFailure) {
        assertThat(staleOrderChangeFailure).isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(OptimisticLockException.class);
    }

    private void assertPersistedOrder(UUID orderId, OrderStatus expectedStatus, long expectedVersion) {
        transactionTemplate.executeWithoutResult(status -> {
            var persistedOrder = orderRepository.findById(orderId).orElseThrow();
            assertThat(persistedOrder.getStatus()).isEqualTo(expectedStatus);
            assertThat(persistedOrder.getVersion()).isEqualTo(expectedVersion);
            assertThat(persistedOrder.getAmountTotal()).isEqualByComparingTo("200.00");
            assertThat(persistedOrder.getItems()).hasSize(1);
            assertThat(persistedOrder.getItems().get(0).getQuantity()).isEqualTo(2L);
        });
    }

    private UUID persistProduct() {
        var savedProduct = productRepository.saveAndFlush(ProductEntity.builder()
                .sku(UUID.randomUUID().toString()).name("Product").price(new BigDecimal("100.00"))
                .quantity(5L).build());
        productIds.add(savedProduct.getId());
        return savedProduct.getId();
    }

    private UUID persistOrder(UUID productId, OrderStatus initialStatus) {
        return transactionTemplate.execute(status -> {
            var savedCustomer = customerRepository.saveAndFlush(
                    new CustomerEntity(UUID.randomUUID() + "@example.com", "Customer"));
            customerIds.add(savedCustomer.getId());
            var product = productRepository.findById(productId).orElseThrow();
            var orderItem = OrderItemEntity.builder().sku(product.getSku())
                    .unitPrice(new BigDecimal("100.00")).quantity(2L).build();
            var savedOrder = orderRepository.saveAndFlush(OrderEntity.builder()
                    .customerId(savedCustomer.getId()).status(initialStatus).addItem(orderItem).build());
            orderIds.add(savedOrder.getId());
            return savedOrder.getId();
        });
    }

    private CreatePaymentAttemptRequest createPaymentRequest() {
        return new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, UUID.randomUUID().toString());
    }
}
