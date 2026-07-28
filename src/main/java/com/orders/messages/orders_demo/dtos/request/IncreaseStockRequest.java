package com.orders.messages.orders_demo.dtos.request;

import jakarta.validation.constraints.Positive;

/**
 * Request payload used to increase the stock of a product.
 *
 * @param quantity the quantity to increase the stock by.
 */
public record IncreaseStockRequest(
        @Positive(message = "Stock change must be a positive value.") Long quantity) {

}
