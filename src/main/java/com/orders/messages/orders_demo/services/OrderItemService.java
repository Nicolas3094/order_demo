package com.orders.messages.orders_demo.services;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.orders.messages.orders_demo.dtos.request.CreateOrderItemRequest;
import com.orders.messages.orders_demo.entity.Order;
import com.orders.messages.orders_demo.entity.OrderItem;
import com.orders.messages.orders_demo.exceptions.order_item.InvalidOrderItemStateException;
import com.orders.messages.orders_demo.exceptions.order_item.OrderItemNotFoundException;
import com.orders.messages.orders_demo.exceptions.orders.OrderNotFoundException;
import com.orders.messages.orders_demo.mappers.OrderItemMapper;
import com.orders.messages.orders_demo.repositories.OrderItemRepository;
import com.orders.messages.orders_demo.repositories.OrderRepository;

import jakarta.transaction.Transactional;

@Service
public class OrderItemService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public OrderItemService(OrderItemRepository orderItemRepository, OrderRepository orderRepository) {
        this.orderItemRepository = orderItemRepository;
        this.orderRepository = orderRepository;
    }

    public OrderItem getOrderItem(UUID orderId, UUID orderItemId) {
        return findOrderItem(orderId, orderItemId);
    }

    @Transactional
    public OrderItem createOrderItem(UUID orderId, CreateOrderItemRequest request) {
        Order order = findOrder(orderId);

        validatePendingOrder(order);

        return orderItemRepository.save(OrderItemMapper.toEntity(request, order));
    }

    @Transactional
    public OrderItem changeUnitPrice(UUID orderId, UUID orderItemId, BigDecimal unitPrice) {
        OrderItem orderItem = findOrderItem(orderId, orderItemId);

        orderItem.changeUnitPrice(unitPrice);

        return orderItemRepository.save(orderItem);
    }

    @Transactional
    public OrderItem changeQuantity(UUID orderId, UUID orderItemId, Long quantity) {
        OrderItem orderItem = findOrderItem(orderId, orderItemId);

        orderItem.changeQuantity(quantity);

        return orderItemRepository.save(orderItem);
    }

    @Transactional
    public void deleteOrderItem(UUID orderId, UUID orderItemId) {
        OrderItem orderItem = findOrderItem(orderId, orderItemId);

        validatePendingOrder(orderItem.getOrder());

        orderItemRepository.delete(orderItem);
    }

    /**
     * Validates if an Order has Pending status.
     * 
     * @param order Thhe order.
     */
    private void validatePendingOrder(Order order) {
        if (!order.canAcceptPayments()) {
            throw new InvalidOrderItemStateException("Only pending orders can modify items.");
        }
    }

    /**
     * Finds the order item if exits, otherwise throws an
     * {@link OrderItemNotFoundException}.
     * 
     * @param orderId     The Order ID.
     * @param orderItemId The Order item ID.
     * @return A complete Order item object.
     */
    private OrderItem findOrderItem(UUID orderId, UUID orderItemId) {
        OrderItem orderItem = orderItemRepository.findById(orderItemId)
                .orElseThrow(OrderItemNotFoundException::new);

        if (!orderId.equals(orderItem.getOrder().getId())) {
            throw new OrderItemNotFoundException();
        }

        return orderItem;
    }

    /**
     * Finds the Order if exits, otherwise throws an
     * {@link OrderNotFoundException}.
     * 
     * @param orderId The Order ID.
     * @return A complete Order object.
     */
    private Order findOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(OrderNotFoundException::new);
    }

}
