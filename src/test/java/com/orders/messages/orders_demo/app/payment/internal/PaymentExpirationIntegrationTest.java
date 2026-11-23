package com.orders.messages.orders_demo.app.payment.internal;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.when;

import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.order.internal.OrderEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderItemEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderRepository;
import com.orders.messages.orders_demo.app.order.internal.OrderStatus;
import com.orders.messages.orders_demo.app.order.internal.exceptions.InvalidOrderStateException;
import com.orders.messages.orders_demo.app.payment.api.CreatePaymentAttemptRequest;
import com.orders.messages.orders_demo.app.payment.internal.exceptions.InvalidPaymentStateException;
import com.orders.messages.orders_demo.app.product.internal.ProductEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductRepository;
import com.orders.messages.orders_demo.app.product.internal.exceptions.ProductNotFoundException;

class PaymentExpirationIntegrationTest extends PostgresContainer {
    private static final Instant EVALUATION_TIME = Instant.parse("2030-01-01T12:00:00Z");
    private static final Duration PAYMENT_LIFETIME = Duration.ofMinutes(15);

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
    @MockitoBean
    private Clock clock;

    private final List<UUID> orderIds = new ArrayList<>();
    private final List<UUID> customerIds = new ArrayList<>();
    private final List<UUID> productIds = new ArrayList<>();
    private UUID productId;
    private UUID orderId;
    private UUID paymentId;
    private String productSku;
    private CreatePaymentAttemptRequest originalCreatePaymentRequest;
    private Long initialPaymentVersion;
    private Long initialOrderVersion;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(EVALUATION_TIME);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        var savedProduct = productRepository.saveAndFlush(ProductEntity.builder()
                .sku(UUID.randomUUID().toString()).name("Product").price(new BigDecimal("100.00"))
                .quantity(5L).build());
        productId = savedProduct.getId();
        productSku = savedProduct.getSku();
        productIds.add(productId);
        orderId = persistOrder();
        originalCreatePaymentRequest = createPaymentRequest();
        paymentId = paymentService.createPaymentAttempt(orderId, originalCreatePaymentRequest).id();
        setCreatedAt(paymentId, EVALUATION_TIME);
        initialPaymentVersion = paymentRepository.findById(paymentId).orElseThrow().getVersion();
        initialOrderVersion = orderRepository.findById(orderId).orElseThrow().getVersion();
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            orderIds.forEach(savedOrderId -> paymentRepository.deleteAll(paymentRepository.findByOrderId(savedOrderId)));
            paymentRepository.flush();
            orderIds.forEach(orderRepository::deleteById);
            orderRepository.flush();
            customerIds.forEach(customerRepository::deleteById);
            customerRepository.flush();
            productIds.forEach(productRepository::deleteById);
            productRepository.flush();
        });
    }

    @ParameterizedTest
    @ValueSource(longs = { -60, 0, 899 })
    void shouldKeepCreatedPaymentReservedBeforeFifteenMinutes(long paymentAgeInSeconds) {
        setCreatedAt(paymentId, EVALUATION_TIME.minusSeconds(paymentAgeInSeconds));

        boolean expired = paymentService.expirePaymentAttemptIfDue(paymentId, EVALUATION_TIME);

        assertThat(expired).isFalse();
        assertPaymentOrderAndStock(PaymentStatus.CREATED, OrderStatus.PAYMENT_IN_PROGRESS, 3L);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getVersion()).isEqualTo(initialPaymentVersion);
        assertThat(orderRepository.findById(orderId).orElseThrow().getVersion()).isEqualTo(initialOrderVersion);
    }

    @ParameterizedTest
    @ValueSource(longs = { 900, 901, 1800 })
    void shouldExpireCreatedPaymentAtOrAfterFifteenMinutesAndRestoreStockWithoutClearingCart(long paymentAgeInSeconds) {
        setCreatedAt(paymentId, EVALUATION_TIME.minusSeconds(paymentAgeInSeconds));

        boolean expired = paymentService.expirePaymentAttemptIfDue(paymentId, EVALUATION_TIME);

        assertThat(expired).isTrue();
        assertPaymentOrderAndStock(PaymentStatus.CANCELLED, OrderStatus.PENDING_PAYMENT, 5L);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getVersion()).isEqualTo(initialPaymentVersion + 1);
        assertThat(orderRepository.findById(orderId).orElseThrow().getVersion()).isEqualTo(initialOrderVersion + 1);
        assertThat(paymentRepository.findByOrderId(orderId)).hasSize(1);
        assertThatThrownBy(() -> paymentService.startProcessing(orderId, paymentId))
                .isInstanceOfAny(InvalidPaymentStateException.class, InvalidOrderStateException.class);
    }

    @Test
    void shouldUseCreationTimeRatherThanRecentUpdateTimeToExpirePayment() {
        setCreatedAt(paymentId, EVALUATION_TIME.minus(PAYMENT_LIFETIME));
        transactionTemplate.executeWithoutResult(status -> entityManager.createNativeQuery(
                "UPDATE payment_attempt SET updated_at = :updatedAt WHERE id = :paymentId")
                .setParameter("updatedAt", EVALUATION_TIME).setParameter("paymentId", paymentId).executeUpdate());

        boolean expired = paymentService.expirePaymentAttemptIfDue(paymentId, EVALUATION_TIME);

        assertThat(expired).isTrue();
        assertPaymentOrderAndStock(PaymentStatus.CANCELLED, OrderStatus.PENDING_PAYMENT, 5L);
    }

    @Test
    void shouldNotAutomaticallyExpireProcessingPaymentWithUnknownProviderOutcome() {
        paymentService.startProcessing(orderId, paymentId);
        Long processingVersion = paymentRepository.findById(paymentId).orElseThrow().getVersion();
        setCreatedAt(paymentId, EVALUATION_TIME.minus(Duration.ofHours(1)));

        boolean expired = paymentService.expirePaymentAttemptIfDue(paymentId, EVALUATION_TIME);

        assertThat(expired).isFalse();
        assertPaymentOrderAndStock(PaymentStatus.PROCESSING, OrderStatus.PAYMENT_IN_PROGRESS, 3L);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getVersion()).isEqualTo(processingVersion);
        assertThat(orderRepository.findById(orderId).orElseThrow().getVersion()).isEqualTo(initialOrderVersion);
    }

    @Test
    void shouldNotAutomaticallyExpireCreatedPaymentThatAlreadyHasProviderReference() {
        setCreatedAt(paymentId, EVALUATION_TIME.minus(Duration.ofHours(1)));
        transactionTemplate.executeWithoutResult(status -> entityManager.createNativeQuery(
                "UPDATE payment_attempt SET provider_ref = :providerRef WHERE id = :paymentId")
                .setParameter("providerRef", "known-provider-operation").setParameter("paymentId", paymentId).executeUpdate());

        boolean expired = paymentService.expirePaymentAttemptIfDue(paymentId, EVALUATION_TIME);

        assertThat(expired).isFalse();
        assertPaymentOrderAndStock(PaymentStatus.CREATED, OrderStatus.PAYMENT_IN_PROGRESS, 3L);
        var unchangedPayment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(unchangedPayment.getProviderRef()).isEqualTo("known-provider-operation");
        assertThat(unchangedPayment.getVersion()).isEqualTo(initialPaymentVersion);
    }

    @Test
    void shouldKeepSucceededPaymentAndPaidOrderUnchangedWhenExpirationRuns() {
        paymentService.startProcessing(orderId, paymentId);
        paymentService.markAsSucceeded(orderId, paymentId, "paid-provider-reference");
        Long succeededVersion = paymentRepository.findById(paymentId).orElseThrow().getVersion();
        Long paidOrderVersion = orderRepository.findById(orderId).orElseThrow().getVersion();
        setCreatedAt(paymentId, EVALUATION_TIME.minus(Duration.ofHours(1)));

        boolean expired = paymentService.expirePaymentAttemptIfDue(paymentId, EVALUATION_TIME);

        assertThat(expired).isFalse();
        assertPaymentOrderAndStock(PaymentStatus.SUCCEEDED, OrderStatus.PAID, 3L);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getVersion()).isEqualTo(succeededVersion);
        assertThat(orderRepository.findById(orderId).orElseThrow().getVersion()).isEqualTo(paidOrderVersion);
    }

    @Test
    void shouldNotRestoreStockAgainForFailedPaymentWhenExpirationRuns() {
        paymentService.startProcessing(orderId, paymentId);
        paymentService.markAsFailed(orderId, paymentId, 402, "Declined");
        Long failedVersion = paymentRepository.findById(paymentId).orElseThrow().getVersion();
        Long pendingOrderVersion = orderRepository.findById(orderId).orElseThrow().getVersion();
        setCreatedAt(paymentId, EVALUATION_TIME.minus(Duration.ofHours(1)));

        boolean expired = paymentService.expirePaymentAttemptIfDue(paymentId, EVALUATION_TIME);

        assertThat(expired).isFalse();
        assertPaymentOrderAndStock(PaymentStatus.FAILED, OrderStatus.PENDING_PAYMENT, 5L);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getVersion()).isEqualTo(failedVersion);
        assertThat(orderRepository.findById(orderId).orElseThrow().getVersion()).isEqualTo(pendingOrderVersion);
    }

    @Test
    void shouldNotRestoreStockAgainForCancelledPaymentWhenExpirationRuns() {
        paymentService.markAsCancelled(orderId, paymentId);
        Long cancelledVersion = paymentRepository.findById(paymentId).orElseThrow().getVersion();
        Long pendingOrderVersion = orderRepository.findById(orderId).orElseThrow().getVersion();
        setCreatedAt(paymentId, EVALUATION_TIME.minus(Duration.ofHours(1)));

        boolean expired = paymentService.expirePaymentAttemptIfDue(paymentId, EVALUATION_TIME);

        assertThat(expired).isFalse();
        assertPaymentOrderAndStock(PaymentStatus.CANCELLED, OrderStatus.PENDING_PAYMENT, 5L);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getVersion()).isEqualTo(cancelledVersion);
        assertThat(orderRepository.findById(orderId).orElseThrow().getVersion()).isEqualTo(pendingOrderVersion);
    }

    @Test
    void shouldExpireOnlyOnceWhenExpirationIsRepeated() {
        setCreatedAt(paymentId, EVALUATION_TIME.minus(PAYMENT_LIFETIME));

        boolean firstExpirationApplied = paymentService.expirePaymentAttemptIfDue(paymentId, EVALUATION_TIME);
        boolean repeatedExpirationApplied = paymentService.expirePaymentAttemptIfDue(paymentId, EVALUATION_TIME.plusSeconds(1));

        assertThat(firstExpirationApplied).isTrue();
        assertThat(repeatedExpirationApplied).isFalse();
        assertPaymentOrderAndStock(PaymentStatus.CANCELLED, OrderStatus.PENDING_PAYMENT, 5L);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getVersion()).isEqualTo(initialPaymentVersion + 1);
        assertThat(orderRepository.findById(orderId).orElseThrow().getVersion()).isEqualTo(initialOrderVersion + 1);
    }

    @Test
    void shouldAllowNewPaymentAttemptAfterExpirationWithoutRecreatingCart() {
        setCreatedAt(paymentId, EVALUATION_TIME.minus(PAYMENT_LIFETIME));
        assertThat(paymentService.expirePaymentAttemptIfDue(paymentId, EVALUATION_TIME)).isTrue();

        var newCreatePaymentRequest = createPaymentRequest();
        var newPaymentResponse = paymentService.createPaymentAttempt(orderId, newCreatePaymentRequest);

        assertThat(newPaymentResponse.id()).isNotEqualTo(paymentId);
        assertThat(newPaymentResponse.orderId()).isEqualTo(orderId);
        assertThat(newPaymentResponse.status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(newPaymentResponse.amount()).isEqualByComparingTo("200.00");
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(paymentRepository.findByOrderId(orderId)).hasSize(2);
        assertThat(productRepository.findById(productId).orElseThrow().getQuantity()).isEqualTo(3L);
        assertCartAndOrder(OrderStatus.PAYMENT_IN_PROGRESS);
    }

    @Test
    void shouldReturnExpiredPaymentForOriginalIdempotencyKeyWithoutReservingStockAgain() {
        setCreatedAt(paymentId, EVALUATION_TIME.minus(PAYMENT_LIFETIME));
        assertThat(paymentService.expirePaymentAttemptIfDue(paymentId, EVALUATION_TIME)).isTrue();

        var repeatedPaymentResponse = paymentService.createPaymentAttempt(orderId, originalCreatePaymentRequest);

        assertThat(repeatedPaymentResponse.id()).isEqualTo(paymentId);
        assertThat(repeatedPaymentResponse.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertPaymentOrderAndStock(PaymentStatus.CANCELLED, OrderStatus.PENDING_PAYMENT, 5L);
        assertThat(paymentRepository.findByOrderId(orderId)).hasSize(1);
    }

    @Test
    void shouldRollBackExpirationWhenReservedProductCannotBeRestored() {
        setCreatedAt(paymentId, EVALUATION_TIME.minus(PAYMENT_LIFETIME));
        productRepository.deleteById(productId);

        assertThatThrownBy(() -> paymentService.expirePaymentAttemptIfDue(paymentId, EVALUATION_TIME))
                .isInstanceOf(ProductNotFoundException.class);

        var unchangedPayment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(unchangedPayment.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(unchangedPayment.getVersion()).isEqualTo(initialPaymentVersion);
        assertCartAndOrder(OrderStatus.PAYMENT_IN_PROGRESS);
        assertThat(orderRepository.findById(orderId).orElseThrow().getVersion()).isEqualTo(initialOrderVersion);
        assertThat(productRepository.findById(productId)).isEmpty();
    }

    @Test
    void shouldRollBackExpirationAndStockRestorationWhenCallerTransactionFails() {
        setCreatedAt(paymentId, EVALUATION_TIME.minus(PAYMENT_LIFETIME));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            assertThat(paymentService.expirePaymentAttemptIfDue(paymentId, EVALUATION_TIME)).isTrue();
            entityManager.flush();
            entityManager.clear();
            assertPaymentOrderAndStock(PaymentStatus.CANCELLED, OrderStatus.PENDING_PAYMENT, 5L);
            throw new IllegalStateException("Caller failed.");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Caller failed.");

        assertPaymentOrderAndStock(PaymentStatus.CREATED, OrderStatus.PAYMENT_IN_PROGRESS, 3L);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getVersion()).isEqualTo(initialPaymentVersion);
        assertThat(orderRepository.findById(orderId).orElseThrow().getVersion()).isEqualTo(initialOrderVersion);
    }

    @Test
    void shouldIgnoreMissingPaymentWithoutChangingExistingReservation() {
        boolean expired = paymentService.expirePaymentAttemptIfDue(UUID.randomUUID(), EVALUATION_TIME);

        assertThat(expired).isFalse();
        assertPaymentOrderAndStock(PaymentStatus.CREATED, OrderStatus.PAYMENT_IN_PROGRESS, 3L);
    }

    @Test
    void shouldExpireOnlyRequestedPaymentWithoutTouchingAnotherEligibleReservation() {
        UUID otherOrderId = persistOrder();
        UUID otherPaymentId = paymentService.createPaymentAttempt(otherOrderId, createPaymentRequest()).id();
        setCreatedAt(paymentId, EVALUATION_TIME.minus(PAYMENT_LIFETIME));
        setCreatedAt(otherPaymentId, EVALUATION_TIME.minus(PAYMENT_LIFETIME));
        Long otherPaymentVersion = paymentRepository.findById(otherPaymentId).orElseThrow().getVersion();
        Long otherOrderVersion = orderRepository.findById(otherOrderId).orElseThrow().getVersion();

        boolean expired = paymentService.expirePaymentAttemptIfDue(paymentId, EVALUATION_TIME);

        assertThat(expired).isTrue();
        assertPaymentOrderAndStock(PaymentStatus.CANCELLED, OrderStatus.PENDING_PAYMENT, 3L);
        var otherPayment = paymentRepository.findById(otherPaymentId).orElseThrow();
        assertThat(otherPayment.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(otherPayment.getVersion()).isEqualTo(otherPaymentVersion);
        var otherOrder = orderRepository.findById(otherOrderId).orElseThrow();
        assertThat(otherOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
        assertThat(otherOrder.getVersion()).isEqualTo(otherOrderVersion);
    }

    @Test
    void shouldAllowStartingProcessingImmediatelyBeforePaymentDeadline() {
        setCreatedAt(paymentId, EVALUATION_TIME.minus(PAYMENT_LIFETIME).plusSeconds(1));

        var processingPaymentResponse = paymentService.startProcessing(orderId, paymentId);

        assertThat(processingPaymentResponse.status()).isEqualTo(PaymentStatus.PROCESSING);
        assertPaymentOrderAndStock(PaymentStatus.PROCESSING, OrderStatus.PAYMENT_IN_PROGRESS, 3L);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getVersion()).isEqualTo(initialPaymentVersion + 1);
    }

    @ParameterizedTest
    @ValueSource(longs = { 900, 901 })
    void shouldRejectStartingProcessingAtOrAfterPaymentDeadlineWithoutChangingReservation(long paymentAgeInSeconds) {
        setCreatedAt(paymentId, EVALUATION_TIME.minusSeconds(paymentAgeInSeconds));

        assertThatThrownBy(() -> paymentService.startProcessing(orderId, paymentId))
                .isInstanceOf(InvalidPaymentStateException.class)
                .hasMessage("Expired payment attempts cannot start processing.");

        assertPaymentOrderAndStock(PaymentStatus.CREATED, OrderStatus.PAYMENT_IN_PROGRESS, 3L);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getVersion()).isEqualTo(initialPaymentVersion);
    }

    @Test
    void shouldKeepReservationWhenProcessingCommitsBeforeStaleExpiration() throws Exception {
        Instant deadline = EVALUATION_TIME.plusSeconds(1);
        setCreatedAt(paymentId, deadline.minus(PAYMENT_LIFETIME));
        var expirationSnapshotRead = new CountDownLatch(1);
        var processingCommitted = new CountDownLatch(1);
        var paymentExecutor = Executors.newSingleThreadExecutor();

        try {
            var expirationFuture = paymentExecutor.submit(
                    () -> expireAfterProcessingCommits(deadline, expirationSnapshotRead, processingCommitted));
            awaitSignal(expirationSnapshotRead, "Expiration must read CREATED before processing starts");

            paymentService.startProcessing(orderId, paymentId);
            when(clock.instant()).thenReturn(deadline);
            processingCommitted.countDown();

            assertThat(expirationFuture.get(20, TimeUnit.SECONDS)).isFalse();
            assertPaymentOrderAndStock(PaymentStatus.PROCESSING, OrderStatus.PAYMENT_IN_PROGRESS, 3L);
            assertThat(paymentRepository.findById(paymentId).orElseThrow().getVersion()).isEqualTo(initialPaymentVersion + 1);
        } finally {
            processingCommitted.countDown();
            paymentExecutor.shutdownNow();
            assertThat(paymentExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void shouldRejectStaleProcessingWhenExpirationCommitsFirstAndRestoreStockOnce() throws Exception {
        Instant deadline = EVALUATION_TIME.plusSeconds(1);
        setCreatedAt(paymentId, deadline.minus(PAYMENT_LIFETIME));
        var processingReady = new CountDownLatch(1);
        var expirationCommitted = new CountDownLatch(1);
        var paymentExecutor = Executors.newSingleThreadExecutor();

        try {
            var processingFuture = paymentExecutor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                paymentService.startProcessing(orderId, paymentId);
                processingReady.countDown();
                awaitSignal(expirationCommitted, "Expiration must commit before the processing update is flushed");
                entityManager.flush();
            }));
            awaitSignal(processingReady, "Processing must be prepared before expiration runs");

            when(clock.instant()).thenReturn(deadline);
            assertThat(paymentService.expirePaymentAttemptIfDue(paymentId, deadline)).isTrue();
            expirationCommitted.countDown();
            Throwable processingFailure = catchThrowable(() -> processingFuture.get(20, TimeUnit.SECONDS));

            assertThat(processingFailure).isInstanceOf(ExecutionException.class);
            assertThat(processingFailure.getCause())
                    .isInstanceOfAny(OptimisticLockException.class, OptimisticLockingFailureException.class);
            assertPaymentOrderAndStock(PaymentStatus.CANCELLED, OrderStatus.PENDING_PAYMENT, 5L);
            assertThat(paymentRepository.findById(paymentId).orElseThrow().getVersion()).isEqualTo(initialPaymentVersion + 1);
        } finally {
            expirationCommitted.countDown();
            paymentExecutor.shutdownNow();
            assertThat(paymentExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private boolean expireAfterProcessingCommits(Instant deadline, CountDownLatch expirationSnapshotRead,
            CountDownLatch processingCommitted) {
        try {
            return transactionTemplate.execute(status -> {
                var originalPayment = paymentRepository.findById(paymentId).orElseThrow();
                assertThat(originalPayment.getStatus()).isEqualTo(PaymentStatus.CREATED);
                expirationSnapshotRead.countDown();
                awaitSignal(processingCommitted, "Processing must commit before stale expiration continues");
                return paymentService.expirePaymentAttemptIfDue(paymentId, deadline);
            });
        } catch (OptimisticLockException | OptimisticLockingFailureException concurrentTransition) {
            return false;
        }
    }

    private void awaitSignal(CountDownLatch signal, String description) {
        try {
            assertThat(signal.await(10, TimeUnit.SECONDS)).as(description).isTrue();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent payment expiration interrupted", interruptedException);
        }
    }

    private void setCreatedAt(UUID savedPaymentId, Instant createdAt) {
        transactionTemplate.executeWithoutResult(status -> entityManager.createNativeQuery(
                "UPDATE payment_attempt SET created_at = :createdAt WHERE id = :paymentId")
                .setParameter("createdAt", createdAt).setParameter("paymentId", savedPaymentId).executeUpdate());
    }

    private void assertPaymentOrderAndStock(PaymentStatus expectedPaymentStatus, OrderStatus expectedOrderStatus,
            long expectedStock) {
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(expectedPaymentStatus);
        assertThat(productRepository.findById(productId).orElseThrow().getQuantity()).isEqualTo(expectedStock);
        assertCartAndOrder(expectedOrderStatus);
    }

    private void assertCartAndOrder(OrderStatus expectedOrderStatus) {
        transactionTemplate.executeWithoutResult(status -> {
            var persistedOrder = orderRepository.findById(orderId).orElseThrow();
            assertThat(persistedOrder.getStatus()).isEqualTo(expectedOrderStatus);
            assertThat(persistedOrder.getAmountTotal()).isEqualByComparingTo("200.00");
            assertThat(persistedOrder.getItems()).hasSize(1);
            assertThat(persistedOrder.getItems().get(0).getSku()).isEqualTo(productSku);
            assertThat(persistedOrder.getItems().get(0).getQuantity()).isEqualTo(2L);
        });
    }

    private UUID persistOrder() {
        return transactionTemplate.execute(status -> {
            var customer = customerRepository.saveAndFlush(
                    new CustomerEntity(UUID.randomUUID() + "@example.com", "Customer"));
            customerIds.add(customer.getId());
            var orderItem = OrderItemEntity.builder().sku(productSku).unitPrice(new BigDecimal("100.00")).quantity(2L).build();
            var savedOrder = orderRepository.saveAndFlush(OrderEntity.builder().customerId(customer.getId()).addItem(orderItem).build());
            orderIds.add(savedOrder.getId());
            return savedOrder.getId();
        });
    }

    private CreatePaymentAttemptRequest createPaymentRequest() {
        return new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, UUID.randomUUID().toString());
    }
}
