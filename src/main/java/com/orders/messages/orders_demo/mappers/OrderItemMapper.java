package com.orders.messages.orders_demo.mappers;

import com.orders.messages.orders_demo.dtos.request.CreateOrderItemRequest;
import com.orders.messages.orders_demo.dtos.response.OrderItemResponse;
import com.orders.messages.orders_demo.entity.OrderItem;

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

    public static OrderItem toEntity(CreateOrderItemRequest request) {
        return new OrderItem(
                request.sku(),
                request.description(),
                request.unitPrice(),
                request.quantity());
    }

}