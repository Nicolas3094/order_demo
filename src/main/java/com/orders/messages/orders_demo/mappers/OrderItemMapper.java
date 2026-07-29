package com.orders.messages.orders_demo.mappers;

import com.orders.messages.orders_demo.dtos.response.OrderItemResponse;
import com.orders.messages.orders_demo.entity.OrderItem;
import com.orders.messages.orders_demo.entity.Product;

public final class OrderItemMapper {

    public static OrderItemResponse toResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getSku(),
                item.getDescription(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getLineTotal());
    }

    public static OrderItem toEntity(long quantity, Product product) {
        return OrderItem.builder()
                .sku(product.getSku())
                .description(product.getDescription())
                .unitPrice(product.getPrice())
                .quantity(quantity)
                .build();
    }

}