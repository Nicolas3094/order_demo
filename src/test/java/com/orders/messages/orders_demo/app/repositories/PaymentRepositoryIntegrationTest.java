package com.orders.messages.orders_demo.app.repositories;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.payment.internal.*;

@Transactional
class PaymentRepositoryIntegrationTest extends PostgresContainer {
    @Autowired
    private PaymentAttemptRepository paymentRepository;

    @Test
    void shouldPersistAndReloadPaymentAttemptWithAllMappedFields() {
        PaymentAttemptEntity saved = paymentRepository.saveAndFlush(createPayment(UUID.randomUUID()));
        entityManager.clear();

        var result = paymentRepository.findById(saved.getId()).orElseThrow();

        assertThat(result).isNotSameAs(saved);
        assertThat(result.getId()).isEqualTo(saved.getId());
        assertThat(result.getOrderId()).isEqualTo(saved.getOrderId());
        assertThat(result.getAmount()).isEqualByComparingTo("247.00");
        assertThat(result.getProvider()).isEqualTo(PaymentProvider.STRIPE);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(result.getIdempotencyKey()).isEqualTo(saved.getIdempotencyKey());
        assertThat(result.getProviderRef()).isNull();
        assertThat(result.getFailureCode()).isNull();
        assertThat(result.getFailureMessage()).isNull();
        assertThat(result.getCreatedAt()).isNotNull().isEqualTo(saved.getCreatedAt());
        assertThat(result.getUpdatedAt()).isNotNull().isEqualTo(saved.getUpdatedAt());
    }

    @Test
    void shouldFindPaymentAttemptByIdempotencyKey() {
        var saved = paymentRepository.saveAndFlush(createPayment(UUID.randomUUID()));
        paymentRepository.saveAndFlush(createPayment(UUID.randomUUID()));
        entityManager.clear();

        var result = paymentRepository.findByIdempotencyKey(saved.getIdempotencyKey());

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getId()).isEqualTo(saved.getId());
    }

    @Test
    void shouldReturnEmptyForUnknownIdempotencyKey() {
        paymentRepository.saveAndFlush(createPayment(UUID.randomUUID()));
        entityManager.clear();

        assertThat(paymentRepository.findByIdempotencyKey(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    void shouldFindOnlyPaymentsBelongingToRequestedOrder() {
        UUID orderId = UUID.randomUUID();
        UUID firstId = paymentRepository.saveAndFlush(createPayment(orderId)).getId();
        UUID secondId = paymentRepository.saveAndFlush(createPayment(orderId)).getId();
        paymentRepository.saveAndFlush(createPayment(UUID.randomUUID()));
        entityManager.clear();

        var result = paymentRepository.findByOrderId(orderId);

        assertThat(result).extracting(PaymentAttemptEntity::getId).containsExactlyInAnyOrder(firstId, secondId);
        assertThat(result).extracting(PaymentAttemptEntity::getOrderId).containsOnly(orderId);
    }

    @Test
    void shouldReturnEmptyListWhenOrderHasNoPayments() {
        paymentRepository.saveAndFlush(createPayment(UUID.randomUUID()));
        entityManager.clear();

        assertThat(paymentRepository.findByOrderId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void shouldRejectDuplicateIdempotencyKey() {
        var saved = paymentRepository.saveAndFlush(createPayment(UUID.randomUUID()));
        entityManager.clear();
        var duplicate = PaymentAttemptEntity.builder().orderId(UUID.randomUUID())
                .amount(new BigDecimal("247.00")).idempotencyKey(saved.getIdempotencyKey()).build();

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("uk_idempotency_key");
    }

    @Test
    void shouldPersistPaymentStatusAndProviderReference() {
        var payment = paymentRepository.saveAndFlush(createPayment(UUID.randomUUID()));
        UUID id = payment.getId();
        entityManager.clear();
        payment = paymentRepository.findById(id).orElseThrow();

        paymentRepository.startProcessingIfAvailable(id);
        entityManager.clear();
        var processing = paymentRepository.findById(id).orElseThrow();
        assertThat(processing.getStatus()).isEqualTo(PaymentStatus.PROCESSING);

        processing.markAsSucceeded("provider-ref-123");
        paymentRepository.saveAndFlush(processing);
        entityManager.clear();

        var result = paymentRepository.findById(id).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(result.getProviderRef()).isEqualTo("provider-ref-123");
    }

    @Test
    void shouldPersistPaymentFailureDetails() {
        var payment = paymentRepository.saveAndFlush(createPayment(UUID.randomUUID()));
        UUID id = payment.getId();
        entityManager.clear();
        payment = paymentRepository.findById(id).orElseThrow();

        paymentRepository.startProcessingIfAvailable(id);
        entityManager.refresh(payment);
        payment.markAsFailed(402, "Payment declined.");
        paymentRepository.saveAndFlush(payment);
        entityManager.clear();

        var result = paymentRepository.findById(id).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.getFailureCode()).isEqualTo(402);
        assertThat(result.getFailureMessage()).isEqualTo("Payment declined.");
    }

    @Test
    void shouldRejectIdempotencyKeyExceedingColumnLength() {
        paymentRepository.saveAndFlush(PaymentAttemptEntity.builder().orderId(UUID.randomUUID())
                .amount(new BigDecimal("247.00")).idempotencyKey("x".repeat(64)).build());
        entityManager.clear();
        var invalid = PaymentAttemptEntity.builder().orderId(UUID.randomUUID())
                .amount(new BigDecimal("247.00")).idempotencyKey("x".repeat(65)).build();

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("value too long for type character varying(64)");
    }

    @Test
    void shouldRejectProviderReferenceExceedingColumnLength() {
        paymentRepository.saveAndFlush(PaymentAttemptEntity.builder().orderId(UUID.randomUUID())
                .amount(new BigDecimal("247.00")).providerRef("x".repeat(100)).build());
        entityManager.clear();
        var invalid = PaymentAttemptEntity.builder().orderId(UUID.randomUUID())
                .amount(new BigDecimal("247.00")).providerRef("x".repeat(101)).build();

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("value too long for type character varying(100)");
    }

    @Test
    void shouldRejectFailureMessageExceedingColumnLength() {
        paymentRepository.saveAndFlush(PaymentAttemptEntity.builder().orderId(UUID.randomUUID())
                .amount(new BigDecimal("247.00")).failureMessage("x".repeat(500)).build());
        entityManager.clear();
        var invalid = PaymentAttemptEntity.builder().orderId(UUID.randomUUID())
                .amount(new BigDecimal("247.00")).failureMessage("x".repeat(501)).build();

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("value too long for type character varying(500)");
    }

    @Test
    void shouldStartProcessingOnlyTheRequestedCreatedPaymentAndReportOneUpdatedRow() {
        var requestedPayment = paymentRepository.saveAndFlush(createPayment(UUID.randomUUID()));
        var otherPayment = paymentRepository.saveAndFlush(createPayment(UUID.randomUUID()));

        int updatedRows = paymentRepository.startProcessingIfAvailable(requestedPayment.getId());
        entityManager.clear();

        assertThat(updatedRows).isEqualTo(1);
        var processingPayment = paymentRepository.findById(requestedPayment.getId()).orElseThrow();
        assertThat(processingPayment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(processingPayment.getOrderId()).isEqualTo(requestedPayment.getOrderId());
        assertThat(processingPayment.getIdempotencyKey()).isEqualTo(requestedPayment.getIdempotencyKey());
        assertThat(processingPayment.getAmount()).isEqualByComparingTo(requestedPayment.getAmount());
        assertThat(paymentRepository.findById(otherPayment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.CREATED);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "CREATED")
    void shouldRejectStartingProcessingForNonCreatedPaymentWithoutChangingIt(PaymentStatus initialStatus) {
        var originalPayment = paymentRepository.saveAndFlush(createPaymentWithStatus(initialStatus));
        Instant originalUpdatedAt = originalPayment.getUpdatedAt();

        int updatedRows = paymentRepository.startProcessingIfAvailable(originalPayment.getId());
        entityManager.clear();

        assertThat(updatedRows).isZero();
        var persistedPayment = paymentRepository.findById(originalPayment.getId()).orElseThrow();
        assertThat(persistedPayment.getStatus()).isEqualTo(initialStatus);
        assertThat(persistedPayment.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    void shouldReportZeroUpdatedRowsWhenStartingProcessingForMissingPayment() {
        assertThat(paymentRepository.startProcessingIfAvailable(UUID.randomUUID())).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = { "CREATED", "PROCESSING" })
    void shouldCancelOnlyTheRequestedEligiblePaymentAndReportOneUpdatedRow(PaymentStatus initialStatus) {
        var requestedPayment = paymentRepository.saveAndFlush(createPaymentWithStatus(initialStatus));
        var otherPayment = paymentRepository.saveAndFlush(createPaymentWithStatus(initialStatus));

        int updatedRows = paymentRepository.cancelIfAvailable(requestedPayment.getId());
        entityManager.clear();

        assertThat(updatedRows).isEqualTo(1);
        var cancelledPayment = paymentRepository.findById(requestedPayment.getId()).orElseThrow();
        assertThat(cancelledPayment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(cancelledPayment.getOrderId()).isEqualTo(requestedPayment.getOrderId());
        assertThat(cancelledPayment.getAmount()).isEqualByComparingTo(requestedPayment.getAmount());
        assertThat(paymentRepository.findById(otherPayment.getId()).orElseThrow().getStatus())
                .isEqualTo(initialStatus);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = { "SUCCEEDED", "FAILED", "CANCELLED" })
    void shouldRejectCancellingTerminalPaymentWithoutChangingIt(PaymentStatus initialStatus) {
        var originalPayment = paymentRepository.saveAndFlush(createPaymentWithStatus(initialStatus));
        Instant originalUpdatedAt = originalPayment.getUpdatedAt();

        int updatedRows = paymentRepository.cancelIfAvailable(originalPayment.getId());
        entityManager.clear();

        assertThat(updatedRows).isZero();
        var persistedPayment = paymentRepository.findById(originalPayment.getId()).orElseThrow();
        assertThat(persistedPayment.getStatus()).isEqualTo(initialStatus);
        assertThat(persistedPayment.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    void shouldReportZeroUpdatedRowsWhenCancellingMissingPayment() {
        assertThat(paymentRepository.cancelIfAvailable(UUID.randomUUID())).isZero();
    }

    @Test
    void shouldUpdateModificationTimestampWhenProcessingStarts() {
        UUID paymentId = paymentRepository.saveAndFlush(createPaymentWithStatus(PaymentStatus.CREATED)).getId();
        Instant previousUpdatedAt = setUpdatedAtInPast(paymentId);

        int updatedRows = paymentRepository.startProcessingIfAvailable(paymentId);
        entityManager.clear();

        assertThat(updatedRows).isEqualTo(1);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getUpdatedAt()).isAfter(previousUpdatedAt);
    }

    @Test
    void shouldUpdateModificationTimestampWhenPaymentIsCancelled() {
        UUID paymentId = paymentRepository.saveAndFlush(createPaymentWithStatus(PaymentStatus.CREATED)).getId();
        Instant previousUpdatedAt = setUpdatedAtInPast(paymentId);

        int updatedRows = paymentRepository.cancelIfAvailable(paymentId);
        entityManager.clear();

        assertThat(updatedRows).isEqualTo(1);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getUpdatedAt()).isAfter(previousUpdatedAt);
    }

    private Instant setUpdatedAtInPast(UUID paymentId) {
        Instant previousUpdatedAt = Instant.parse("2000-01-01T00:00:00Z");
        entityManager.createNativeQuery("UPDATE payment_attempt SET updated_at = :updatedAt WHERE id = :paymentId")
                .setParameter("updatedAt", previousUpdatedAt)
                .setParameter("paymentId", paymentId)
                .executeUpdate();
        entityManager.clear();
        return previousUpdatedAt;
    }

    private PaymentAttemptEntity createPaymentWithStatus(PaymentStatus paymentStatus) {
        return PaymentAttemptEntity.builder().orderId(UUID.randomUUID()).provider(PaymentProvider.STRIPE)
                .amount(new BigDecimal("247.00")).idempotencyKey(UUID.randomUUID().toString())
                .status(paymentStatus).build();
    }

    private PaymentAttemptEntity createPayment(UUID orderId) {
        return PaymentAttemptEntity.builder().orderId(orderId).provider(PaymentProvider.STRIPE)
                .amount(new BigDecimal("247.00")).idempotencyKey(UUID.randomUUID().toString()).build();
    }
}
