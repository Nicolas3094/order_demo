package com.orders.messages.orders_demo.app.payment.internal;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.context.TestPropertySource;
import java.util.concurrent.CyclicBarrier;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;

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
@TestPropertySource(properties = {
        "payments.reconciliation.batch-size=2",
        "payments.reconciliation.processing-timeout=PT15M",
        "payments.reconciliation.initial-delay=3600000",
        "payments.expiration.initial-delay=3600000"
})
class PaymentReconciliationIntegrationTest extends PostgresContainer {
    private static final Instant EVALUATION_TIME = Instant.parse("2030-01-01T12:00:00Z");
    private static final Duration PAYMENT_LIFETIME = Duration.ofMinutes(15);

    @Autowired
    private PaymentAttemptService paymentService;
    @MockitoBean
    private PaymentProviderGateway paymentProviderGateway;
    @Autowired
    private PaymentReconciliationJob paymentReconciliationJob;
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
        paymentService.startProcessing(orderId, paymentId);
        setUpdatedAt(paymentId, EVALUATION_TIME.minus(PAYMENT_LIFETIME));
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
    void shouldCompleteOrderForConfirmedSuccessWithoutRestoringStock() {
        var providerResult = successfulProviderResult();
        when(paymentProviderGateway.lookupPayment(PaymentProvider.STRIPE,
                originalCreatePaymentRequest.idempotencyKey(), null)).thenReturn(providerResult);

        paymentReconciliationJob.reconcileProcessingPayments();

        assertPaymentOrderAndStock(PaymentStatus.SUCCEEDED, OrderStatus.PAID, 23L);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getProviderRef()).isEqualTo("provider-success");
        verify(paymentProviderGateway).lookupPayment(PaymentProvider.STRIPE,
                originalCreatePaymentRequest.idempotencyKey(), null);
    }

    @Test
    void shouldRecordConfirmedFailureAndRestoreStockWithoutClearingCart() {
        when(paymentProviderGateway.lookupPayment(any(), any(), any())).thenReturn(failedProviderResult());

        paymentReconciliationJob.reconcileProcessingPayments();

        assertPaymentOrderAndStock(PaymentStatus.FAILED, OrderStatus.PENDING_PAYMENT, 25L);
        var failedPayment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(failedPayment.getFailureCode()).isEqualTo(402);
        assertThat(failedPayment.getFailureMessage()).isEqualTo("Provider declined payment.");
    }

    @Test
    void shouldCancelOnlyAfterProviderConfirmsCancellationAndRestoreStock() {
        var cancelledProviderResult = new PaymentProviderResult(PaymentProviderOutcome.CANCELLED, null, null, null);
        when(paymentProviderGateway.lookupPayment(any(), any(), any())).thenReturn(cancelledProviderResult);

        paymentReconciliationJob.reconcileProcessingPayments();

        assertPaymentOrderAndStock(PaymentStatus.CANCELLED, OrderStatus.PENDING_PAYMENT, 25L);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentProviderOutcome.class, names = { "PENDING", "UNKNOWN" })
    void shouldKeepReservationAndVersionsWhenProviderCannotConfirmTerminalOutcome(PaymentProviderOutcome outcome) {
        when(paymentProviderGateway.lookupPayment(any(), any(), any()))
                .thenReturn(new PaymentProviderResult(outcome, null, null, null));

        paymentReconciliationJob.reconcileProcessingPayments();

        assertUnchangedProcessingReservation();
        verify(paymentProviderGateway).lookupPayment(PaymentProvider.STRIPE,
                originalCreatePaymentRequest.idempotencyKey(), null);
    }

    @Test
    void shouldKeepReservationWhenProviderLookupFailsAndRetryOnNextExecution() {
        when(paymentProviderGateway.lookupPayment(any(), any(), any()))
                .thenThrow(new IllegalStateException("Provider unavailable."))
                .thenReturn(failedProviderResult());

        paymentReconciliationJob.reconcileProcessingPayments();
        assertUnchangedProcessingReservation();
        paymentReconciliationJob.reconcileProcessingPayments();

        assertPaymentOrderAndStock(PaymentStatus.FAILED, OrderStatus.PENDING_PAYMENT, 25L);
        verify(paymentProviderGateway, times(2)).lookupPayment(PaymentProvider.STRIPE,
                originalCreatePaymentRequest.idempotencyKey(), null);
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 899 })
    void shouldNotConsultProviderBeforeProcessingTimeoutEvenForOldCreatedPayment(long processingAgeInSeconds) {
        setCreatedAt(paymentId, EVALUATION_TIME.minus(Duration.ofHours(1)));
        setUpdatedAt(paymentId, EVALUATION_TIME.minusSeconds(processingAgeInSeconds));

        paymentReconciliationJob.reconcileProcessingPayments();

        verifyNoInteractions(paymentProviderGateway);
        assertUnchangedProcessingReservation();
    }

    @Test
    void shouldConsultProviderUsingExistingProviderReferenceWhenAvailable() {
        transactionTemplate.executeWithoutResult(status -> entityManager.createNativeQuery(
                "UPDATE payment_attempt SET provider_ref = :providerRef WHERE id = :paymentId")
                .setParameter("providerRef", "known-operation").setParameter("paymentId", paymentId).executeUpdate());
        when(paymentProviderGateway.lookupPayment(PaymentProvider.STRIPE,
                originalCreatePaymentRequest.idempotencyKey(), "known-operation"))
                .thenReturn(new PaymentProviderResult(PaymentProviderOutcome.PENDING, "known-operation", null, null));

        paymentReconciliationJob.reconcileProcessingPayments();

        verify(paymentProviderGateway).lookupPayment(PaymentProvider.STRIPE,
                originalCreatePaymentRequest.idempotencyKey(), "known-operation");
        assertUnchangedProcessingReservation();
    }

    @Test
    void shouldNotConsultProviderForCreatedPayment() {
        transactionTemplate.executeWithoutResult(status -> entityManager.createNativeQuery(
                "UPDATE payment_attempt SET status = 'CREATED' WHERE id = :paymentId")
                .setParameter("paymentId", paymentId).executeUpdate());

        paymentReconciliationJob.reconcileProcessingPayments();

        verifyNoInteractions(paymentProviderGateway);
        assertPaymentOrderAndStock(PaymentStatus.CREATED, OrderStatus.PAYMENT_IN_PROGRESS, 23L);
    }

    @Test
    void shouldNotConsultProviderOrRestoreStockAgainForTerminalPayment() {
        paymentService.markAsFailed(orderId, paymentId, 402, "Provider declined payment.");
        setUpdatedAt(paymentId, EVALUATION_TIME.minus(PAYMENT_LIFETIME));
        Long failedPaymentVersion = paymentRepository.findById(paymentId).orElseThrow().getVersion();

        paymentReconciliationJob.reconcileProcessingPayments();

        verifyNoInteractions(paymentProviderGateway);
        assertPaymentOrderAndStock(PaymentStatus.FAILED, OrderStatus.PENDING_PAYMENT, 25L);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getVersion()).isEqualTo(failedPaymentVersion);
    }

    @Test
    void shouldNotRestoreStockOrQueryAgainAfterRepeatedSuccessfulReconciliation() {
        when(paymentProviderGateway.lookupPayment(any(), any(), any())).thenReturn(failedProviderResult());

        paymentReconciliationJob.reconcileProcessingPayments();
        paymentReconciliationJob.reconcileProcessingPayments();

        assertPaymentOrderAndStock(PaymentStatus.FAILED, OrderStatus.PENDING_PAYMENT, 25L);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getVersion()).isEqualTo(initialPaymentVersion + 1);
        verify(paymentProviderGateway).lookupPayment(PaymentProvider.STRIPE,
                originalCreatePaymentRequest.idempotencyKey(), null);
    }

    @Test
    void shouldContinueAcrossBatchesWhenOneProviderLookupFails() {
        UUID secondPaymentId = persistAdditionalProcessingPayment();
        UUID thirdPaymentId = persistAdditionalProcessingPayment();
        UUID fourthPaymentId = persistAdditionalProcessingPayment();
        UUID fifthPaymentId = persistAdditionalProcessingPayment();
        when(paymentProviderGateway.lookupPayment(any(), any(), any())).thenReturn(failedProviderResult());
        when(paymentProviderGateway.lookupPayment(PaymentProvider.STRIPE,
                originalCreatePaymentRequest.idempotencyKey(), null))
                .thenThrow(new IllegalStateException("Provider unavailable for this attempt."));

        paymentReconciliationJob.reconcileProcessingPayments();

        assertReconciledFailure(secondPaymentId);
        assertReconciledFailure(thirdPaymentId);
        assertReconciledFailure(fourthPaymentId);
        assertReconciledFailure(fifthPaymentId);
        assertUnchangedProcessingReservation();
        verify(paymentProviderGateway, times(5)).lookupPayment(any(), any(), any());
    }

    @Test
    void shouldRollBackFailedStockRestorationAndKeepPaymentProcessing() {
        when(paymentProviderGateway.lookupPayment(any(), any(), any())).thenReturn(failedProviderResult());
        productRepository.deleteById(productId);

        paymentReconciliationJob.reconcileProcessingPayments();

        var unchangedPayment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(unchangedPayment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(unchangedPayment.getVersion()).isEqualTo(initialPaymentVersion);
        assertCartAndOrder(OrderStatus.PAYMENT_IN_PROGRESS);
        assertThat(orderRepository.findById(orderId).orElseThrow().getVersion()).isEqualTo(initialOrderVersion);
        verify(paymentProviderGateway).lookupPayment(PaymentProvider.STRIPE,
                originalCreatePaymentRequest.idempotencyKey(), null);
    }

    @Test
    void shouldPreserveWebhookSuccessCommittedWhileProviderLookupWasInProgress() {
        when(paymentProviderGateway.lookupPayment(any(), any(), any())).thenAnswer(invocation -> {
            paymentService.markAsSucceeded(orderId, paymentId, "webhook-success");
            return failedProviderResult();
        });

        paymentReconciliationJob.reconcileProcessingPayments();

        assertPaymentOrderAndStock(PaymentStatus.SUCCEEDED, OrderStatus.PAID, 23L);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getProviderRef()).isEqualTo("webhook-success");
    }

    @Test
    void shouldRestoreStockOnceWhenTwoReconciliationsReceiveTheSameFailure() throws Exception {
        var providerLookupBarrier = new CyclicBarrier(2);
        when(paymentProviderGateway.lookupPayment(any(), any(), any())).thenAnswer(invocation -> {
            awaitProviderLookups(providerLookupBarrier);
            return failedProviderResult();
        });
        var reconciliationExecutor = Executors.newFixedThreadPool(2);

        try {
            var firstReconciliationFuture = reconciliationExecutor.submit(paymentReconciliationJob::reconcileProcessingPayments);
            var secondReconciliationFuture = reconciliationExecutor.submit(paymentReconciliationJob::reconcileProcessingPayments);

            firstReconciliationFuture.get(20, TimeUnit.SECONDS);
            secondReconciliationFuture.get(20, TimeUnit.SECONDS);

            assertPaymentOrderAndStock(PaymentStatus.FAILED, OrderStatus.PENDING_PAYMENT, 25L);
            assertThat(paymentRepository.findById(paymentId).orElseThrow().getVersion()).isEqualTo(initialPaymentVersion + 1);
        } finally {
            reconciliationExecutor.shutdownNow();
            assertThat(reconciliationExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void shouldKeepReservationWhenProviderReturnsSuccessWithoutReference() {
        when(paymentProviderGateway.lookupPayment(any(), any(), any()))
                .thenReturn(new PaymentProviderResult(PaymentProviderOutcome.SUCCEEDED, null, null, null));

        paymentReconciliationJob.reconcileProcessingPayments();

        assertUnchangedProcessingReservation();
        verify(paymentProviderGateway).lookupPayment(PaymentProvider.STRIPE,
                originalCreatePaymentRequest.idempotencyKey(), null);
    }

    @Test
    void shouldConsultProviderOutsideDatabaseTransactionBeforeApplyingConfirmedOutcome() {
        when(paymentProviderGateway.lookupPayment(any(), any(), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return successfulProviderResult();
        });

        paymentReconciliationJob.reconcileProcessingPayments();

        assertPaymentOrderAndStock(PaymentStatus.SUCCEEDED, OrderStatus.PAID, 23L);
        verify(paymentProviderGateway).lookupPayment(PaymentProvider.STRIPE,
                originalCreatePaymentRequest.idempotencyKey(), null);
    }

    @Test
    void shouldKeepReservationWhenProviderReturnsNoResult() {
        when(paymentProviderGateway.lookupPayment(any(), any(), any())).thenReturn(null);

        paymentReconciliationJob.reconcileProcessingPayments();

        assertUnchangedProcessingReservation();
        verify(paymentProviderGateway).lookupPayment(PaymentProvider.STRIPE,
                originalCreatePaymentRequest.idempotencyKey(), null);
    }

    @Test
    void shouldSuspendCallerTransactionAndCommitReconciliationIndependently() {
        when(paymentProviderGateway.lookupPayment(any(), any(), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return successfulProviderResult();
        });

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            paymentReconciliationJob.reconcileProcessingPayments();
            throw new IllegalStateException("Caller failed.");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Caller failed.");

        assertPaymentOrderAndStock(PaymentStatus.SUCCEEDED, OrderStatus.PAID, 23L);
    }

    @ParameterizedTest
    @MethodSource("invalidProviderResults")
    void shouldKeepReservationWhenProviderReturnsIncompleteOrInvalidDetails(PaymentProviderResult invalidProviderResult) {
        when(paymentProviderGateway.lookupPayment(any(), any(), any())).thenReturn(invalidProviderResult);

        paymentReconciliationJob.reconcileProcessingPayments();

        assertUnchangedProcessingReservation();
        verify(paymentProviderGateway).lookupPayment(PaymentProvider.STRIPE,
                originalCreatePaymentRequest.idempotencyKey(), null);
    }

    private static Stream<PaymentProviderResult> invalidProviderResults() {
        return Stream.of(
                new PaymentProviderResult(null, null, null, null),
                new PaymentProviderResult(PaymentProviderOutcome.SUCCEEDED, " ", null, null),
                new PaymentProviderResult(PaymentProviderOutcome.FAILED, null, null, "Declined"),
                new PaymentProviderResult(PaymentProviderOutcome.FAILED, null, 200, "Declined"),
                new PaymentProviderResult(PaymentProviderOutcome.FAILED, null, 402, null),
                new PaymentProviderResult(PaymentProviderOutcome.FAILED, null, 402, " "));
    }

    private UUID persistAdditionalProcessingPayment() {
        UUID additionalOrderId = persistOrder();
        UUID additionalPaymentId = paymentService.createPaymentAttempt(additionalOrderId, createPaymentRequest()).id();
        setCreatedAt(additionalPaymentId, EVALUATION_TIME);
        paymentService.startProcessing(additionalOrderId, additionalPaymentId);
        setUpdatedAt(additionalPaymentId, EVALUATION_TIME.minus(PAYMENT_LIFETIME));
        return additionalPaymentId;
    }

    private void assertReconciledFailure(UUID reconciledPaymentId) {
        var failedPayment = paymentRepository.findById(reconciledPaymentId).orElseThrow();
        assertThat(failedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(orderRepository.findById(failedPayment.getOrderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    private void assertUnchangedProcessingReservation() {
        assertPaymentOrderAndStock(PaymentStatus.PROCESSING, OrderStatus.PAYMENT_IN_PROGRESS, 23L);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getVersion()).isEqualTo(initialPaymentVersion);
        assertThat(orderRepository.findById(orderId).orElseThrow().getVersion()).isEqualTo(initialOrderVersion);
    }

    private PaymentProviderResult successfulProviderResult() {
        return new PaymentProviderResult(PaymentProviderOutcome.SUCCEEDED, "provider-success", null, null);
    }

    private PaymentProviderResult failedProviderResult() {
        return new PaymentProviderResult(PaymentProviderOutcome.FAILED, null, 402, "Provider declined payment.");
    }

    private void setUpdatedAt(UUID savedPaymentId, Instant updatedAt) {
        transactionTemplate.executeWithoutResult(status -> entityManager.createNativeQuery(
                "UPDATE payment_attempt SET updated_at = :updatedAt WHERE id = :paymentId")
                .setParameter("updatedAt", updatedAt).setParameter("paymentId", savedPaymentId).executeUpdate());
    }

    private void awaitProviderLookups(CyclicBarrier providerLookupBarrier) {
        try {
            providerLookupBarrier.await(10, TimeUnit.SECONDS);
        } catch (Exception lookupFailure) {
            throw new IllegalStateException("Both reconciliations must consult the provider.", lookupFailure);
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
