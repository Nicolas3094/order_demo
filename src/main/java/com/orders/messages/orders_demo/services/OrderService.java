package com.orders.messages.orders_demo.services;

import java.util.UUID;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.orders.messages.orders_demo.entity.Order;
import com.orders.messages.orders_demo.exceptions.OrderNotFoundException;
import com.orders.messages.orders_demo.repositories.OrderRepository;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository repository) {
        this.orderRepository = repository;
    }

    public Order getOrder(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(OrderNotFoundException::new);

    }

    public void createOrder(@NonNull Order newOrder) {
        orderRepository.save(newOrder);
    }

    public void cancelOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(OrderNotFoundException::new);

        order.cancelOrder();

        orderRepository.save(order);
    }

    public void payOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(OrderNotFoundException::new);

        order.markAsPaid();

        orderRepository.save(order);
    }

    public void expireOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(OrderNotFoundException::new);

        order.expire();

        orderRepository.save(order);
    }

    public void refundOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(OrderNotFoundException::new);

        order.refund();

        orderRepository.save(order);
    }

}
