package com.orders.messages.orders_demo.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orders.messages.orders_demo.entity.Order;

public interface OrderRepository extends JpaRepository<Order, UUID> {

}
