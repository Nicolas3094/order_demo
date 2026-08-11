package com.orders.messages.orders_demo.app.order_item.internal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, UUID> {

}
