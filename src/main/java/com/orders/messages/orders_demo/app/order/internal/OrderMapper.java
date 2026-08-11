package com.orders.messages.orders_demo.app.order.internal;

import java.util.UUID;

import com.orders.messages.orders_demo.app.order.api.CreateOrderRequest;
import com.orders.messages.orders_demo.app.order.api.OrderResponse;

public final class OrderMapper {

    public static OrderResponse toResponse(OrderEntity order) {
        return OrderResponse.builder()
                .setId(order.getId())
                .customerId(order.getCustomerId())
                .amountTotal(order.getAmountTotal())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .currency(order.getCurrency())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .expiresAt(order.getExpiresAt())
                .build();
    }

    public static OrderEntity toEntity(CreateOrderRequest orderRequest, UUID customerId) {
        return OrderEntity.builder()
                .customerId(customerId)
                .currency(orderRequest.currency())
                .build();
    }

}
