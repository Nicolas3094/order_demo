package com.orders.messages.orders_demo.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateOrderItemRequest(
        @NotBlank(message = "Order item must have SKU.") String sku,
        @Positive(message = "Order item quantity must be positive.") Long quantity) {

}
