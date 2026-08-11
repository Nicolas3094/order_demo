package com.orders.messages.orders_demo.app.order;

import org.springframework.stereotype.Service;

import com.orders.messages.orders_demo.app.order.api.OrderInternalApi;
import com.orders.messages.orders_demo.app.order.internal.OrderEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderRepository;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderNotFoundException;

@Service
public class OrderInternalApiImpl implements OrderInternalApi {
    private final OrderRepository orderRepository;

    public OrderInternalApiImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public OrderEntity getOrderById(java.util.UUID id) {
        return orderRepository.findById(id).orElseThrow(OrderNotFoundException::new);
    }

    @Override
    public OrderEntity saveOrder(OrderEntity order) {
        return orderRepository.save(order);
    }
}
