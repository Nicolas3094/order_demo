package com.orders.messages.orders_demo.app.payment.internal;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.util.concurrent.CyclicBarrier;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;

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

@Timeout(30)
@TestPropertySource(properties = "payments.expiration.batch-size=2")
class PaymentExpirationJobIntegrationTest extends PostgresContainer {
    private static final Instant EVALUATION_TIME = Instant.parse("2030-01-01T12:00:00Z");
    private static final Duration PAYMENT_LIFETIME = Duration.ofMinutes(15);

    @MockitoSpyBean
    private PaymentAttemptService paymentService;
    @Autowired
    private PaymentExpirationJob paymentExpirationJob;
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
                .quantity(25L).build());
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

    @Test
    void shouldExpireEveryDuePaymentAcrossMultipleBatchesWithoutSkippingRows() {
        UUID secondPaymentId = persistAdditionalDuePayment();
        UUID thirdPaymentId = persistAdditionalDuePayment();
        UUID fourthPaymentId = persistAdditionalDuePayment();
        UUID fifthPaymentId = persistAdditionalDuePayment();
        setCreatedAt(paymentId, EVALUATION_TIME.minus(PAYMENT_LIFETIME));

        paymentExpirationJob.expireDuePayments();

        assertExpiredReservation(paymentId);
        assertExpiredReservation(secondPaymentId);
        assertExpiredReservation(thirdPaymentId);
        assertExpiredReservation(fourthPaymentId);
        assertExpiredReservation(fifthPaymentId);
        assertThat(productRepository.findById(productId).orElseThrow().getQuantity()).isEqualTo(25L);
    }

    @Test
    void shouldIncludeExactDeadlineAndExcludeNewerPayments() {
        UUID duePaymentId = persistAdditionalDuePayment();
        setCreatedAt(paymentId, EVALUATION_TIME.minus(PAYMENT_LIFETIME).plusSeconds(1));

        paymentExpirationJob.expireDuePayments();

        assertExpiredReservation(duePaymentId);
        assertPaymentOrderAndStock(PaymentStatus.CREATED, OrderStatus.PAYMENT_IN_PROGRESS, 23L);
    }

    @Test
    void shouldSkipProcessingAndKnownProviderReferenceWhileExpiringEligiblePayments() {
        paymentService.startProcessing(orderId, paymentId);
        setCreatedAt(paymentId, EVALUATION_TIME.minus(PAYMENT_LIFETIME));
        UUID knownReferencePaymentId = persistAdditionalDuePayment();
        transactionTemplate.executeWithoutResult(status -> entityManager.createNativeQuery(
                "UPDATE payment_attempt SET provider_ref = :providerRef WHERE id = :paymentId")
                .setParameter("providerRef", "known-operation").setParameter("paymentId", knownReferencePaymentId)
                .executeUpdate());
        UUID eligiblePaymentId = persistAdditionalDuePayment();

        paymentExpirationJob.expireDuePayments();

        assertExpiredReservation(eligiblePaymentId);
        assertThat(paymentRepository.findById(knownReferencePaymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.CREATED);
        assertThat(paymentRepository.findById(knownReferencePaymentId).orElseThrow().getProviderRef())
                .isEqualTo("known-operation");
        assertPaymentOrderAndStock(PaymentStatus.PROCESSING, OrderStatus.PAYMENT_IN_PROGRESS, 21L);
    }

    @Test
    void shouldContinueAfterFailedExpirationAndCommitOtherPaymentsIndependently() {
        setCreatedAt(paymentId, EVALUATION_TIME.minus(Duration.ofMinutes(20)));
        UUID secondPaymentId = persistAdditionalDuePayment();
        UUID thirdPaymentId = persistAdditionalDuePayment();
        UUID fourthPaymentId = persistAdditionalDuePayment();
        doAnswer(invocation -> {
            transactionTemplate.executeWithoutResult(status -> {
                paymentService.expirePaymentAttemptIfDue(paymentId, EVALUATION_TIME.minusSeconds(1));
                entityManager.flush();
                throw new IllegalStateException("Expiration failed after restoring stock.");
            });
            return false;
        }).when(paymentService).expirePaymentAttemptIfDue(eq(paymentId), eq(EVALUATION_TIME));

        paymentExpirationJob.expireDuePayments();

        assertExpiredReservation(secondPaymentId);
        assertExpiredReservation(thirdPaymentId);
        assertExpiredReservation(fourthPaymentId);
        assertPaymentOrderAndStock(PaymentStatus.CREATED, OrderStatus.PAYMENT_IN_PROGRESS, 23L);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getVersion()).isEqualTo(initialPaymentVersion);
    }

    @Test
    void shouldNotRestoreStockAgainWhenScheduledExecutionIsRepeated() {
        setCreatedAt(paymentId, EVALUATION_TIME.minus(PAYMENT_LIFETIME));

        paymentExpirationJob.expireDuePayments();
        paymentExpirationJob.expireDuePayments();

        assertPaymentOrderAndStock(PaymentStatus.CANCELLED, OrderStatus.PENDING_PAYMENT, 25L);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getVersion()).isEqualTo(initialPaymentVersion + 1);
        assertThat(orderRepository.findById(orderId).orElseThrow().getVersion()).isEqualTo(initialOrderVersion + 1);
    }

    @Test
    void shouldRestoreStockOnlyOnceWhenTwoExecutionsReadTheSameDuePayment() throws Exception {
        setCreatedAt(paymentId, EVALUATION_TIME.minus(PAYMENT_LIFETIME));
        var expirationReadBarrier = new CyclicBarrier(2);
        doAnswer(invocation -> transactionTemplate.execute(status -> {
            paymentRepository.findById(paymentId).orElseThrow();
            awaitExpirationReaders(expirationReadBarrier);
            try {
                return (Boolean) invocation.callRealMethod();
            } catch (RuntimeException runtimeException) {
                throw runtimeException;
            } catch (Throwable unexpectedFailure) {
                throw new IllegalStateException(unexpectedFailure);
            }
        })).when(paymentService).expirePaymentAttemptIfDue(eq(paymentId), eq(EVALUATION_TIME));
        var expirationExecutor = Executors.newFixedThreadPool(2);

        try {
            var firstExpirationFuture = expirationExecutor.submit(paymentExpirationJob::expireDuePayments);
            var secondExpirationFuture = expirationExecutor.submit(paymentExpirationJob::expireDuePayments);

            firstExpirationFuture.get(20, TimeUnit.SECONDS);
            secondExpirationFuture.get(20, TimeUnit.SECONDS);

            assertPaymentOrderAndStock(PaymentStatus.CANCELLED, OrderStatus.PENDING_PAYMENT, 25L);
            assertThat(paymentRepository.findById(paymentId).orElseThrow().getVersion()).isEqualTo(initialPaymentVersion + 1);
            assertThat(orderRepository.findById(orderId).orElseThrow().getVersion()).isEqualTo(initialOrderVersion + 1);
        } finally {
            expirationExecutor.shutdownNow();
            assertThat(expirationExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void shouldLeaveReservationsUnchangedWhenNoPaymentsAreDue() {
        paymentExpirationJob.expireDuePayments();

        assertPaymentOrderAndStock(PaymentStatus.CREATED, OrderStatus.PAYMENT_IN_PROGRESS, 23L);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getVersion()).isEqualTo(initialPaymentVersion);
    }

    private UUID persistAdditionalDuePayment() {
        UUID additionalOrderId = persistOrder();
        UUID additionalPaymentId = paymentService.createPaymentAttempt(additionalOrderId, createPaymentRequest()).id();
        setCreatedAt(additionalPaymentId, EVALUATION_TIME.minus(PAYMENT_LIFETIME));
        return additionalPaymentId;
    }

    private void assertExpiredReservation(UUID expiredPaymentId) {
        var expiredPayment = paymentRepository.findById(expiredPaymentId).orElseThrow();
        assertThat(expiredPayment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        transactionTemplate.executeWithoutResult(status -> {
            var pendingOrder = orderRepository.findById(expiredPayment.getOrderId()).orElseThrow();
            assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(pendingOrder.getAmountTotal()).isEqualByComparingTo("200.00");
            assertThat(pendingOrder.getItems()).hasSize(1);
            assertThat(pendingOrder.getItems().get(0).getQuantity()).isEqualTo(2L);
        });
    }

    private void awaitExpirationReaders(CyclicBarrier expirationReadBarrier) {
        try {
            expirationReadBarrier.await(10, TimeUnit.SECONDS);
        } catch (Exception barrierFailure) {
            throw new IllegalStateException("Both executions must read the same payment before expiration.", barrierFailure);
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
