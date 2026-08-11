package com.orders.messages.orders_demo.app.order_item.internal;

import com.orders.messages.orders_demo.app.order_item.api.OrderItemResponse;
import com.orders.messages.orders_demo.app.product.internal.ProductEntity;

public final class OrderItemMapper {

    public static OrderItemResponse toResponse(OrderItemEntity item) {
        return new OrderItemResponse(
                item.getId(),
                item.getSku(),
                item.getDescription(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getLineTotal());
    }

    public static OrderItemEntity toEntity(long quantity, ProductEntity product) {
        return OrderItemEntity.builder()
                .sku(product.getSku())
                .description(product.getDescription())
                .unitPrice(product.getPrice())
                .quantity(quantity)
                .build();
    }

}