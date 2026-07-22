package com.orders.messages.orders_demo.dtos.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateOrderItemRequest(
        @NotBlank(message = "Order item must have SKU.") String sku,
        @NotBlank(message = "Order item must have description.") String description,
        @Positive(message = "Order item unit price must be positive.") BigDecimal unitPrice,
        @Positive(message = "Order item quantity must be positive.") Long quantity) {

}
