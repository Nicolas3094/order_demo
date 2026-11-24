package com.orders.messages.orders_demo.app.payment.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class PaymentReconciliationJob {
    private static final Logger logger = LoggerFactory.getLogger(PaymentReconciliationJob.class);

    private final PaymentAttemptRepository paymentRepository;
    private final PaymentAttemptService paymentService;
    private final ObjectProvider<PaymentProviderGateway> providerGateway;
    private final Clock clock;
    private final Duration processingTimeout;
    private final int batchSize;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate outcomeTransaction;

    public PaymentReconciliationJob(PaymentAttemptRepository paymentRepository,
            PaymentAttemptService paymentService, ObjectProvider<PaymentProviderGateway> providerGateway,
            PlatformTransactionManager transactionManager, Clock clock,
            @Value("${payments.reconciliation.processing-timeout:PT15M}") Duration processingTimeout,
            @Value("${payments.reconciliation.batch-size:100}") int batchSize) {
        if (processingTimeout.isNegative() || processingTimeout.isZero()) {
            throw new IllegalArgumentException("Payment processing timeout must be positive.");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("Payment reconciliation batch size must be positive.");
        }
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.providerGateway = providerGateway;
        this.clock = clock;
        this.processingTimeout = processingTimeout;
        this.batchSize = batchSize;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.readTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.outcomeTransaction = new TransactionTemplate(transactionManager);
        this.outcomeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${payments.reconciliation.fixed-delay:60000}",
            initialDelayString = "${payments.reconciliation.initial-delay:60000}")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void reconcileProcessingPayments() {
        PaymentProviderGateway gateway = providerGateway.getIfAvailable();
        if (gateway == null) {
            logger.debug("Payment reconciliation skipped: no provider gateway is configured.");
            return;
        }

        Instant deadline = clock.instant().minus(processingTimeout);
        UUID afterId = null;
        while (true) {
            var paymentIds = paymentRepository.findProcessingPaymentIdsDueForReconciliation(
                    PaymentStatus.PROCESSING, deadline, afterId, PageRequest.of(0, batchSize));
            if (paymentIds.isEmpty()) {
                return;
            }
            for (UUID paymentId : paymentIds) {
                try {
                    reconcilePayment(paymentId, deadline, gateway);
                } catch (RuntimeException reconciliationFailure) {
                    logger.warn("Could not reconcile payment {}; it will be reconsidered on the next run.",
                            paymentId, reconciliationFailure);
                }
            }
            // Move beyond failures and unchanged payments as well as completed payments.
            afterId = paymentIds.get(paymentIds.size() - 1);
        }
    }

    private void reconcilePayment(UUID paymentId, Instant deadline, PaymentProviderGateway gateway) {
        PaymentAttemptEntity paymentSnapshot = readTransaction.execute(status ->
                paymentRepository.findById(paymentId).orElse(null));
        if (paymentSnapshot == null || paymentSnapshot.getStatus() != PaymentStatus.PROCESSING
                || paymentSnapshot.getUpdatedAt().isAfter(deadline)) {
            return;
        }

        // The provider call runs after the read transaction has finished, without holding database locks.
        PaymentProviderResult providerResult = gateway.lookupPayment(paymentSnapshot.getProvider(),
                paymentSnapshot.getIdempotencyKey(), paymentSnapshot.getProviderRef());
        if (!hasConfirmedOutcome(providerResult)) {
            return;
        }

        outcomeTransaction.executeWithoutResult(status -> {
            var currentPayment = paymentRepository.findById(paymentId).orElse(null);
            if (currentPayment == null || currentPayment.getStatus() != PaymentStatus.PROCESSING) {
                return;
            }
            // Re-read after the remote lookup; @Version also protects changes that race with this transaction.
            switch (providerResult.outcome()) {
                case SUCCEEDED -> paymentService.markAsSucceeded(currentPayment.getOrderId(), paymentId,
                        providerResult.providerRef());
                case FAILED -> paymentService.markAsFailed(currentPayment.getOrderId(), paymentId,
                        providerResult.failureCode(), providerResult.failureMessage());
                case CANCELLED -> paymentService.markAsCancelled(currentPayment.getOrderId(), paymentId);
                default -> throw new IllegalStateException("Provider outcome is not terminal.");
            }
        });
    }

    private boolean hasConfirmedOutcome(PaymentProviderResult providerResult) {
        if (providerResult == null || providerResult.outcome() == null) {
            return false;
        }
        return switch (providerResult.outcome()) {
            case SUCCEEDED -> providerResult.providerRef() != null && !providerResult.providerRef().isBlank()
                    && providerResult.providerRef().length() <= 100;
            case FAILED -> providerResult.failureCode() != null
                    && providerResult.failureCode() >= 400 && providerResult.failureCode() <= 599
                    && providerResult.failureMessage() != null && !providerResult.failureMessage().isBlank()
                    && providerResult.failureMessage().length() <= 500;
            case CANCELLED -> true;
            case PENDING, UNKNOWN -> false;
        };
    }
}
