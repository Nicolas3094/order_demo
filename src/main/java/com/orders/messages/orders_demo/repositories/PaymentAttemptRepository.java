package com.orders.messages.orders_demo.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orders.messages.orders_demo.entity.PaymentAttempt;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {
    Optional<PaymentAttempt> findByIdempotencyKey(String idempotencyKey);

    List<PaymentAttempt> findByOrderId(UUID orderId);
}
