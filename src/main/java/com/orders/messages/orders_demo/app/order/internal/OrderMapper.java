package com.orders.messages.orders_demo.app.order.internal;

import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.order.api.CreateOrderRequest;
import com.orders.messages.orders_demo.app.order.api.OrderResponse;

public final class OrderMapper {

    public static OrderResponse toResponse(OrderEntity order) {
        return OrderResponse.builder()
                .setId(order.getId())
                .setCustomerId(order.getCustomer().getId())
                .setAmountTotal(order.getAmountTotal())
                .setStatus(order.getStatus())
                .setCreatedAt(order.getCreatedAt())
                .setCurrency(order.getCurrency())
                .setCreatedAt(order.getCreatedAt())
                .setUpdatedAt(order.getUpdatedAt())
                .setExpiresAt(order.getExpiresAt())
                .build();
    }

    public static OrderEntity toEntity(CreateOrderRequest orderRequest, CustomerEntity customer) {
        return OrderEntity.builder()
                .customer(customer)
                .currency(orderRequest.currency())
                .build();
    }

}
