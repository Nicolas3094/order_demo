package com.orders.messages.orders_demo.app.order.internal;

import java.util.Optional;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    Page<OrderEntity> findByCustomerId(UUID customerId, Pageable pageable);

    Optional<OrderEntity> findByCustomerIdAndStatusIn(UUID customerId, Collection<OrderStatus> statuses);

    Optional<OrderEntity> findByIdAndCustomerId(UUID id, UUID customerId);

    Page<OrderEntity> findByCustomerIdAndStatus(UUID customerId, OrderStatus status, Pageable pageable);

}
