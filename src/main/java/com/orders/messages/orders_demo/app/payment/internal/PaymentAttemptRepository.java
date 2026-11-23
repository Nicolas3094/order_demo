package com.orders.messages.orders_demo.app.payment.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttemptEntity, UUID> {
    Optional<PaymentAttemptEntity> findByIdempotencyKey(String idempotencyKey);

    List<PaymentAttemptEntity> findByOrderId(UUID orderId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE payment_attempt
            SET status = 'PROCESSING', updated_at = CURRENT_TIMESTAMP
            WHERE id = :paymentAttemptId
                AND status = 'CREATED'
            """, nativeQuery = true)
    int startProcessingIfAvailable(@Param("paymentAttemptId") UUID paymentAttemptId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE payment_attempt
            SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP
            WHERE id = :paymentAttemptId
                AND (status = 'CREATED' or status = 'PROCESSING')
            """, nativeQuery = true)
    int cancelIfAvailable(@Param("paymentAttemptId") UUID paymentAttemptId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE payment_attempt
            SET status = 'SUCCEEDED',
                updated_at = CURRENT_TIMESTAMP,
                provider_ref = :providerRef
            WHERE idempotency_key = :idempotencyKey
                AND status = 'PROCESSING'
            """, nativeQuery = true)
    int markAsSucceededIfAvailable(
            @Param("idempotencyKey") String idempotencyKey,
            @Param("providerRef") String providerRef);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE payment_attempt
            SET status = 'FAILED',
                updated_at = CURRENT_TIMESTAMP,
                failure_code = :failureCode,
                failure_message = :failureMessage
            WHERE idempotency_key = :idempotencyKey
                AND status = 'PROCESSING'
            """, nativeQuery = true)
    int markAsFailedIfAvailable(
            @Param("idempotencyKey") String idempotencyKey,
            @Param("failureCode") Integer failureCode,
            @Param("failureMessage") String failureMessage);
}
