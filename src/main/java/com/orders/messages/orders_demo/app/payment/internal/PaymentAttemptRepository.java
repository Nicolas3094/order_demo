package com.orders.messages.orders_demo.app.payment.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttemptEntity, UUID> {
    Optional<PaymentAttemptEntity> findByIdempotencyKey(String idempotencyKey);

    List<PaymentAttemptEntity> findByOrderId(UUID orderId);

    boolean existsByProviderAndProviderRef(PaymentProvider provider, String providerRef);

    @Transactional(readOnly = true)
    @Query("""
            SELECT payment.id FROM PaymentAttemptEntity payment
            WHERE payment.status = :status AND payment.providerRef IS NULL
              AND payment.createdAt <= :deadline
              AND (:afterId IS NULL OR payment.id > :afterId)
            ORDER BY payment.id ASC
            """)
    List<UUID> findDuePaymentIds(@Param("status") PaymentStatus status,
            @Param("deadline") Instant deadline, @Param("afterId") UUID afterId, Pageable pageable);

    @Transactional(readOnly = true)
    @Query("""
            SELECT payment.id FROM PaymentAttemptEntity payment
            WHERE payment.status = :status AND payment.updatedAt <= :deadline
              AND (:afterId IS NULL OR payment.id > :afterId)
            ORDER BY payment.id ASC
            """)
    List<UUID> findProcessingPaymentIdsDueForReconciliation(@Param("status") PaymentStatus status,
            @Param("deadline") Instant deadline, @Param("afterId") UUID afterId, Pageable pageable);

}
