package com.orders.messages.orders_demo.dtos.request;

import jakarta.validation.constraints.Positive;

/**
 * Request payload used to decrease the stock of a product.
 *
 * @param quantity the quantity to decrease the stock by.
 */
public record DecreaseStockRequest(
        @Positive(message = "Stock change must be a positive value.") Long quantity) {

}
