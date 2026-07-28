package com.orders.messages.orders_demo.mappers;

import com.orders.messages.orders_demo.dtos.request.CreateProductRequest;
import com.orders.messages.orders_demo.dtos.response.ProductResponse;
import com.orders.messages.orders_demo.entity.Product;

public final class ProductMapper {
    private ProductMapper() {
    }

    public static ProductResponse toResponse(Product item) {
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

    public static Product toEntity(CreateProductRequest request) {
        return Product.builder()
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
