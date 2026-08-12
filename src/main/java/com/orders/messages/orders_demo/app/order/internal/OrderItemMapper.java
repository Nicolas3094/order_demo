package com.orders.messages.orders_demo.app.order.internal;

import com.orders.messages.orders_demo.app.order.api.OrderItemResponse;
import com.orders.messages.orders_demo.app.product.api.ProductResponse;

public final class OrderItemMapper {

    public static OrderItemResponse toResponse(OrderItemEntity item) {
        return new OrderItemResponse(
                item.getId(),
                item.getOrder().getId(),
                item.getSku(),
                item.getDescription(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getLineTotal());
    }

    public static OrderItemEntity toEntity(long quantity, ProductResponse product) {
        return OrderItemEntity.builder()
                .sku(product.sku())
                .description(product.description())
                .unitPrice(product.price())
                .quantity(quantity)
                .build();
    }

}