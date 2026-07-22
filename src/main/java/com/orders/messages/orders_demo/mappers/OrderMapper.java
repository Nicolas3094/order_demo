package com.orders.messages.orders_demo.mappers;

import com.orders.messages.orders_demo.dtos.request.CreateOrderRequest;
import com.orders.messages.orders_demo.dtos.response.OrderResponse;
import com.orders.messages.orders_demo.entity.Customer;
import com.orders.messages.orders_demo.entity.Order;

public final class OrderMapper {

    public static OrderResponse toResponse(Order order) {
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

    public static Order toEntity(CreateOrderRequest orderRequest, Customer customer) {
        return new Order(customer, orderRequest.currency(), orderRequest.amountTotal());
    }

}
