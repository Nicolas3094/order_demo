package com.orders.messages.orders_demo.dtos.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.Positive;

/**
 * Request payload used to change the price of a product.
 *
 * @param price the new price of the product.
 */
public record ChangePriceRequest(
        @Positive(message = "Price must be a positive value.") BigDecimal price) {

}
