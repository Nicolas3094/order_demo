package com.orders.messages.orders_demo.app.product.api;

import com.orders.messages.orders_demo.app.common.enums.Currency;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload used to change the currency of a product.
 *
 * @param currency the new currency to set for the product.
 */
public record ChangeCurrencyRequest(
        @NotNull(message = "Currency is required.") Currency currency) {

}
