package com.orders.messages.orders_demo.app.order;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.orders.messages.orders_demo.app.order.api.OrderData;
import com.orders.messages.orders_demo.app.order.api.OrderInternalApi;
import com.orders.messages.orders_demo.app.order.api.OrderResponse;
import com.orders.messages.orders_demo.app.order.internal.OrderEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderMapper;
import com.orders.messages.orders_demo.app.order.internal.OrderRepository;
import com.orders.messages.orders_demo.app.order.internal.exceptions.InvalidOrderStateException;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderNotFoundException;

@Service
public class OrderInternalApiImpl implements OrderInternalApi {
    private final OrderRepository orderRepository;

    public OrderInternalApiImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public OrderResponse getOrderById(UUID id) {
        OrderEntity order = getOrder(id);

        return OrderMapper.toResponse(order);
    }

    @Override
    public void markAsPaid(UUID id) {
        OrderEntity order = getOrder(id);

        order.markAsPaid();

        orderRepository.save(order);
    }

    @Override
    public void validateCanReceivePayment(UUID id) {
        OrderEntity order = getOrder(id);

        if (!order.canAcceptPayments()) {
            throw new InvalidOrderStateException("This order cannot receive payment attempts.");
        }
    }

    @Override
    public OrderData getOrderDataForPayment(UUID orderId) {
        OrderEntity order = getOrder(orderId);

        if (!order.canAcceptPayments()) {
            throw new InvalidOrderStateException(
                    "This order cannot receive payment attempts.");
        }

        return new OrderData(
                order.getId(),
                order.getAmountTotal());
    }

    @Override
    public void validateOrderExists(UUID orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new OrderNotFoundException(orderId);
        }
    }

    private OrderEntity getOrder(UUID id) {
        return orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }
}
