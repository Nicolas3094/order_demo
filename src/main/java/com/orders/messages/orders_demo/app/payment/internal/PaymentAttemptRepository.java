package com.orders.messages.orders_demo.app.payment.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttemptEntity, UUID> {
    Optional<PaymentAttemptEntity> findByIdempotencyKey(String idempotencyKey);

    List<PaymentAttemptEntity> findByOrderId(UUID orderId);
}
