package com.orders.messages.orders_demo.app.product.internal;

import com.orders.messages.orders_demo.app.product.api.CreateProductRequest;
import com.orders.messages.orders_demo.app.product.api.ProductResponse;

public final class ProductMapper {
    private ProductMapper() {
    }

    public static ProductResponse toResponse(ProductEntity item) {
        return ProductResponse.builder()
                .id(item.getId())
                .sku(item.getSku())
                .active(item.getActive())
                .currency(item.getCurrency())
                .description(item.getDescription())
                .name(item.getName())
                .price(item.getPrice())
                .quantity(item.getQuantity())
                .build();
    }

    public static ProductEntity toEntity(CreateProductRequest request) {
        return ProductEntity.builder()
                .sku(request.sku())
                .description(request.description())
                .name(request.name())
                .price(request.price())
                .active(request.active())
                .quantity(request.quantity())
                .currency(request.currency())
                .build();
    }
}
