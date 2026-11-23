package com.orders.messages.orders_demo.app.payment.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class PaymentExpirationJob {
    private static final Logger logger = LoggerFactory.getLogger(PaymentExpirationJob.class);
    private static final Duration PAYMENT_LIFETIME = Duration.ofMinutes(15);

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentAttemptService paymentAttemptService;
    private final TransactionTemplate expirationTransaction;
    private final Clock clock;
    private final int batchSize;

    public PaymentExpirationJob(PaymentAttemptRepository paymentAttemptRepository,
            PaymentAttemptService paymentAttemptService, PlatformTransactionManager transactionManager,
            Clock clock, @Value("${payments.expiration.batch-size:100}") int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Payment expiration batch size must be positive.");
        }
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentAttemptService = paymentAttemptService;
        this.clock = clock;
        this.batchSize = batchSize;
        this.expirationTransaction = new TransactionTemplate(transactionManager);
        this.expirationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${payments.expiration.fixed-delay:60000}",
            initialDelayString = "${payments.expiration.initial-delay:60000}")
    public void expireDuePayments() {
        Instant evaluationTime = clock.instant();
        Instant deadline = evaluationTime.minus(PAYMENT_LIFETIME);
        UUID afterId = null;

        while (true) {
            var duePaymentIds = paymentAttemptRepository.findDuePaymentIds(
                    PaymentStatus.CREATED, deadline, afterId, PageRequest.of(0, batchSize));
            if (duePaymentIds.isEmpty()) {
                return;
            }

            for (UUID paymentId : duePaymentIds) {
                try {
                    expirationTransaction.executeWithoutResult(status ->
                            paymentAttemptService.expirePaymentAttemptIfDue(paymentId, evaluationTime));
                } catch (RuntimeException expirationFailure) {
                    logger.warn("Could not expire payment {}; it will be reconsidered on the next run.",
                            paymentId, expirationFailure);
                }
            }
            // A stable cursor avoids skipping rows as successful expirations leave the result set.
            // Failed attempts are retried on the next scheduled execution, not repeatedly in this run.
            afterId = duePaymentIds.get(duePaymentIds.size() - 1);
        }
    }
}
