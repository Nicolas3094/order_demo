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
import org.springframework.dao.OptimisticLockingFailureException;
import com.orders.messages.orders_demo.app.payment.internal.exceptions.InvalidPaymentStateException;
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
        assertThat(result.getVersion()).isZero();
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

        payment.startProcessing();
        paymentRepository.saveAndFlush(payment);
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

        payment.startProcessing();
        paymentRepository.saveAndFlush(payment);
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
    void shouldPersistProcessingAndIncrementOnlyTheRequestedPaymentVersion() {
        var requestedPayment = paymentRepository.saveAndFlush(createPaymentWithStatus(PaymentStatus.CREATED));
        var otherPayment = paymentRepository.saveAndFlush(createPaymentWithStatus(PaymentStatus.CREATED));
        Long initialVersion = requestedPayment.getVersion();

        requestedPayment.startProcessing();
        paymentRepository.saveAndFlush(requestedPayment);
        entityManager.clear();

        var updatedPayment = paymentRepository.findById(requestedPayment.getId()).orElseThrow();
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(updatedPayment.getVersion()).isEqualTo(initialVersion + 1);
        assertThat(updatedPayment.getOrderId()).isEqualTo(requestedPayment.getOrderId());
        assertThat(updatedPayment.getIdempotencyKey()).isEqualTo(requestedPayment.getIdempotencyKey());
        assertThat(updatedPayment.getAmount()).isEqualByComparingTo(requestedPayment.getAmount());
        var unchangedPayment = paymentRepository.findById(otherPayment.getId()).orElseThrow();
        assertThat(unchangedPayment.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(unchangedPayment.getVersion()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "CREATED")
    void shouldRejectInvalidProcessingWithoutChangingPersistedStateVersionOrTimestamp(PaymentStatus initialStatus) {
        var originalPayment = paymentRepository.saveAndFlush(createPaymentWithStatus(initialStatus));
        Instant originalUpdatedAt = originalPayment.getUpdatedAt();
        Long originalVersion = originalPayment.getVersion();

        assertThatThrownBy(() -> originalPayment.startProcessing())
                .isInstanceOf(InvalidPaymentStateException.class)
                .hasMessage("Only created payments can start processing.");
        entityManager.clear();

        var persistedPayment = paymentRepository.findById(originalPayment.getId()).orElseThrow();
        assertThat(persistedPayment.getStatus()).isEqualTo(initialStatus);
        assertThat(persistedPayment.getVersion()).isEqualTo(originalVersion);
        assertThat(persistedPayment.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    void shouldUpdateModificationTimestampWhenProcessingIsPersisted() {
        var originalPayment = paymentRepository.saveAndFlush(createPaymentWithStatus(PaymentStatus.CREATED));
        Instant previousUpdatedAt = setUpdatedAtInPast(originalPayment.getId());
        var managedPayment = paymentRepository.findById(originalPayment.getId()).orElseThrow();

        managedPayment.startProcessing();
        paymentRepository.saveAndFlush(managedPayment);
        entityManager.clear();

        var persistedPayment = paymentRepository.findById(originalPayment.getId()).orElseThrow();
        assertThat(persistedPayment.getUpdatedAt()).isAfter(previousUpdatedAt);
        assertThat(persistedPayment.getCreatedAt()).isEqualTo(originalPayment.getCreatedAt());
        assertThat(persistedPayment.getVersion()).isEqualTo(originalPayment.getVersion() + 1);
    }

    @Test
    void shouldPersistCancellationAndIncrementOnlyTheRequestedPaymentVersion() {
        var requestedPayment = paymentRepository.saveAndFlush(createPaymentWithStatus(PaymentStatus.PROCESSING));
        var otherPayment = paymentRepository.saveAndFlush(createPaymentWithStatus(PaymentStatus.PROCESSING));
        Long initialVersion = requestedPayment.getVersion();

        requestedPayment.cancel();
        paymentRepository.saveAndFlush(requestedPayment);
        entityManager.clear();

        var updatedPayment = paymentRepository.findById(requestedPayment.getId()).orElseThrow();
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(updatedPayment.getVersion()).isEqualTo(initialVersion + 1);
        assertThat(updatedPayment.getOrderId()).isEqualTo(requestedPayment.getOrderId());
        assertThat(updatedPayment.getIdempotencyKey()).isEqualTo(requestedPayment.getIdempotencyKey());
        assertThat(updatedPayment.getAmount()).isEqualByComparingTo(requestedPayment.getAmount());
        var unchangedPayment = paymentRepository.findById(otherPayment.getId()).orElseThrow();
        assertThat(unchangedPayment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(unchangedPayment.getVersion()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, mode = EnumSource.Mode.EXCLUDE, names = { "CREATED", "PROCESSING" })
    void shouldRejectInvalidCancellationWithoutChangingPersistedStateVersionOrTimestamp(PaymentStatus initialStatus) {
        var originalPayment = paymentRepository.saveAndFlush(createPaymentWithStatus(initialStatus));
        Instant originalUpdatedAt = originalPayment.getUpdatedAt();
        Long originalVersion = originalPayment.getVersion();

        assertThatThrownBy(() -> originalPayment.cancel())
                .isInstanceOf(InvalidPaymentStateException.class)
                .hasMessage("Only created or processing payments can be cancelled.");
        entityManager.clear();

        var persistedPayment = paymentRepository.findById(originalPayment.getId()).orElseThrow();
        assertThat(persistedPayment.getStatus()).isEqualTo(initialStatus);
        assertThat(persistedPayment.getVersion()).isEqualTo(originalVersion);
        assertThat(persistedPayment.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    void shouldUpdateModificationTimestampWhenCancellationIsPersisted() {
        var originalPayment = paymentRepository.saveAndFlush(createPaymentWithStatus(PaymentStatus.PROCESSING));
        Instant previousUpdatedAt = setUpdatedAtInPast(originalPayment.getId());
        var managedPayment = paymentRepository.findById(originalPayment.getId()).orElseThrow();

        managedPayment.cancel();
        paymentRepository.saveAndFlush(managedPayment);
        entityManager.clear();

        var persistedPayment = paymentRepository.findById(originalPayment.getId()).orElseThrow();
        assertThat(persistedPayment.getUpdatedAt()).isAfter(previousUpdatedAt);
        assertThat(persistedPayment.getCreatedAt()).isEqualTo(originalPayment.getCreatedAt());
        assertThat(persistedPayment.getVersion()).isEqualTo(originalPayment.getVersion() + 1);
    }

    @Test
    void shouldPersistSuccessAndIncrementOnlyTheRequestedPaymentVersion() {
        var requestedPayment = paymentRepository.saveAndFlush(createPaymentWithStatus(PaymentStatus.PROCESSING));
        var otherPayment = paymentRepository.saveAndFlush(createPaymentWithStatus(PaymentStatus.PROCESSING));
        Long initialVersion = requestedPayment.getVersion();

        requestedPayment.markAsSucceeded("success-ref");
        paymentRepository.saveAndFlush(requestedPayment);
        entityManager.clear();

        var updatedPayment = paymentRepository.findById(requestedPayment.getId()).orElseThrow();
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(updatedPayment.getVersion()).isEqualTo(initialVersion + 1);
        assertThat(updatedPayment.getOrderId()).isEqualTo(requestedPayment.getOrderId());
        assertThat(updatedPayment.getIdempotencyKey()).isEqualTo(requestedPayment.getIdempotencyKey());
        assertThat(updatedPayment.getAmount()).isEqualByComparingTo(requestedPayment.getAmount());
        var unchangedPayment = paymentRepository.findById(otherPayment.getId()).orElseThrow();
        assertThat(unchangedPayment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(unchangedPayment.getVersion()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "PROCESSING")
    void shouldRejectInvalidSuccessWithoutChangingPersistedStateVersionOrTimestamp(PaymentStatus initialStatus) {
        var originalPayment = paymentRepository.saveAndFlush(createPaymentWithStatus(initialStatus));
        Instant originalUpdatedAt = originalPayment.getUpdatedAt();
        Long originalVersion = originalPayment.getVersion();

        assertThatThrownBy(() -> originalPayment.markAsSucceeded("success-ref"))
                .isInstanceOf(InvalidPaymentStateException.class)
                .hasMessage("Only processing payments can be marked as succeeded.");
        entityManager.clear();

        var persistedPayment = paymentRepository.findById(originalPayment.getId()).orElseThrow();
        assertThat(persistedPayment.getStatus()).isEqualTo(initialStatus);
        assertThat(persistedPayment.getVersion()).isEqualTo(originalVersion);
        assertThat(persistedPayment.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    void shouldUpdateModificationTimestampWhenSuccessIsPersisted() {
        var originalPayment = paymentRepository.saveAndFlush(createPaymentWithStatus(PaymentStatus.PROCESSING));
        Instant previousUpdatedAt = setUpdatedAtInPast(originalPayment.getId());
        var managedPayment = paymentRepository.findById(originalPayment.getId()).orElseThrow();

        managedPayment.markAsSucceeded("success-ref");
        paymentRepository.saveAndFlush(managedPayment);
        entityManager.clear();

        var persistedPayment = paymentRepository.findById(originalPayment.getId()).orElseThrow();
        assertThat(persistedPayment.getUpdatedAt()).isAfter(previousUpdatedAt);
        assertThat(persistedPayment.getCreatedAt()).isEqualTo(originalPayment.getCreatedAt());
        assertThat(persistedPayment.getVersion()).isEqualTo(originalPayment.getVersion() + 1);
    }

    @Test
    void shouldPersistFailureAndIncrementOnlyTheRequestedPaymentVersion() {
        var requestedPayment = paymentRepository.saveAndFlush(createPaymentWithStatus(PaymentStatus.PROCESSING));
        var otherPayment = paymentRepository.saveAndFlush(createPaymentWithStatus(PaymentStatus.PROCESSING));
        Long initialVersion = requestedPayment.getVersion();

        requestedPayment.markAsFailed(402, "Declined");
        paymentRepository.saveAndFlush(requestedPayment);
        entityManager.clear();

        var updatedPayment = paymentRepository.findById(requestedPayment.getId()).orElseThrow();
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(updatedPayment.getVersion()).isEqualTo(initialVersion + 1);
        assertThat(updatedPayment.getOrderId()).isEqualTo(requestedPayment.getOrderId());
        assertThat(updatedPayment.getIdempotencyKey()).isEqualTo(requestedPayment.getIdempotencyKey());
        assertThat(updatedPayment.getAmount()).isEqualByComparingTo(requestedPayment.getAmount());
        var unchangedPayment = paymentRepository.findById(otherPayment.getId()).orElseThrow();
        assertThat(unchangedPayment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(unchangedPayment.getVersion()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "PROCESSING")
    void shouldRejectInvalidFailureWithoutChangingPersistedStateVersionOrTimestamp(PaymentStatus initialStatus) {
        var originalPayment = paymentRepository.saveAndFlush(createPaymentWithStatus(initialStatus));
        Instant originalUpdatedAt = originalPayment.getUpdatedAt();
        Long originalVersion = originalPayment.getVersion();

        assertThatThrownBy(() -> originalPayment.markAsFailed(402, "Declined"))
                .isInstanceOf(InvalidPaymentStateException.class)
                .hasMessage("Only processing payments can fail.");
        entityManager.clear();

        var persistedPayment = paymentRepository.findById(originalPayment.getId()).orElseThrow();
        assertThat(persistedPayment.getStatus()).isEqualTo(initialStatus);
        assertThat(persistedPayment.getVersion()).isEqualTo(originalVersion);
        assertThat(persistedPayment.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    void shouldUpdateModificationTimestampWhenFailureIsPersisted() {
        var originalPayment = paymentRepository.saveAndFlush(createPaymentWithStatus(PaymentStatus.PROCESSING));
        Instant previousUpdatedAt = setUpdatedAtInPast(originalPayment.getId());
        var managedPayment = paymentRepository.findById(originalPayment.getId()).orElseThrow();

        managedPayment.markAsFailed(402, "Declined");
        paymentRepository.saveAndFlush(managedPayment);
        entityManager.clear();

        var persistedPayment = paymentRepository.findById(originalPayment.getId()).orElseThrow();
        assertThat(persistedPayment.getUpdatedAt()).isAfter(previousUpdatedAt);
        assertThat(persistedPayment.getCreatedAt()).isEqualTo(originalPayment.getCreatedAt());
        assertThat(persistedPayment.getVersion()).isEqualTo(originalPayment.getVersion() + 1);
    }

    @Test
    void shouldRejectSavingDetachedPaymentWithStaleVersion() {
        var stalePayment = paymentRepository.saveAndFlush(createPaymentWithStatus(PaymentStatus.CREATED));
        entityManager.detach(stalePayment);
        var currentPayment = paymentRepository.findById(stalePayment.getId()).orElseThrow();
        currentPayment.startProcessing();
        paymentRepository.saveAndFlush(currentPayment);
        entityManager.clear();

        stalePayment.cancel();

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(stalePayment))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void shouldDefaultVersionToZeroForRowsInsertedWithoutVersion() {
        UUID paymentId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                INSERT INTO payment_attempt (id, order_id, amount, provider, status, idempotency_key)
                VALUES (:paymentId, :orderId, 247.00, 'STRIPE', 'CREATED', :idempotencyKey)
                """)
                .setParameter("paymentId", paymentId)
                .setParameter("orderId", UUID.randomUUID())
                .setParameter("idempotencyKey", UUID.randomUUID().toString())
                .executeUpdate();
        entityManager.clear();

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getVersion()).isZero();
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
