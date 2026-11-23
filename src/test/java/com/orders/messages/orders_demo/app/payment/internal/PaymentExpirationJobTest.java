package com.orders.messages.orders_demo.app.payment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class PaymentExpirationJobTest {
    @Test
    void shouldScheduleExpirationEveryMinuteByDefaultWithConfigurableDelay() throws Exception {
        var expirationMethod = PaymentExpirationJob.class.getDeclaredMethod("expireDuePayments");
        var scheduledConfiguration = expirationMethod.getAnnotation(Scheduled.class);

        assertThat(scheduledConfiguration).isNotNull();
        assertThat(scheduledConfiguration.fixedDelayString())
                .isEqualTo("${payments.expiration.fixed-delay:60000}");
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, -1 })
    void shouldRejectNonPositiveBatchSize(int batchSize) {
        var paymentRepository = mock(PaymentAttemptRepository.class);
        var paymentService = mock(PaymentAttemptService.class);
        var transactionManager = mock(PlatformTransactionManager.class);

        assertThatThrownBy(() -> new PaymentExpirationJob(paymentRepository, paymentService,
                transactionManager, Clock.systemUTC(), batchSize))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Payment expiration batch size must be positive.");
    }

    @Test
    void shouldUseNewTransactionForEachPaymentAndKeepEvaluationTimeAcrossBatches() {
        Instant evaluationTime = Instant.parse("2030-01-01T12:00:00Z");
        Instant deadline = evaluationTime.minusSeconds(900);
        UUID firstPaymentId = UUID.randomUUID();
        UUID secondPaymentId = UUID.randomUUID();
        var paymentRepository = mock(PaymentAttemptRepository.class);
        var paymentService = mock(PaymentAttemptService.class);
        var transactionManager = mock(PlatformTransactionManager.class);
        var firstTransactionStatus = mock(TransactionStatus.class);
        var secondTransactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(firstTransactionStatus, secondTransactionStatus);
        when(paymentRepository.findDuePaymentIds(PaymentStatus.CREATED, deadline, null, PageRequest.of(0, 1)))
                .thenReturn(List.of(firstPaymentId));
        when(paymentRepository.findDuePaymentIds(PaymentStatus.CREATED, deadline, firstPaymentId, PageRequest.of(0, 1)))
                .thenReturn(List.of(secondPaymentId));
        when(paymentRepository.findDuePaymentIds(PaymentStatus.CREATED, deadline, secondPaymentId, PageRequest.of(0, 1)))
                .thenReturn(List.of());
        var paymentExpirationJob = new PaymentExpirationJob(paymentRepository, paymentService,
                transactionManager, Clock.fixed(evaluationTime, ZoneOffset.UTC), 1);

        paymentExpirationJob.expireDuePayments();

        verify(paymentService).expirePaymentAttemptIfDue(firstPaymentId, evaluationTime);
        verify(paymentService).expirePaymentAttemptIfDue(secondPaymentId, evaluationTime);
        var transactionDefinitions = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager, times(2)).getTransaction(transactionDefinitions.capture());
        assertThat(transactionDefinitions.getAllValues()).extracting(TransactionDefinition::getPropagationBehavior)
                .containsExactly(TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        verify(transactionManager).commit(firstTransactionStatus);
        verify(transactionManager).commit(secondTransactionStatus);
    }
}
