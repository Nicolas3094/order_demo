package com.orders.messages.orders_demo.dtos.request;

import java.util.UUID;

import com.orders.messages.orders_demo.enums.Currency;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload used to create a new order.
 *
 * @param customerId identifier of the customer who owns the order.
 * @param currency   ISO 4217 currency code (e.g. MXN, USD).
 */
public record CreateOrderRequest(
        @NotNull(message = "Customer id is required.") UUID customerId,
        @NotNull(message = "Currency is required.") Currency currency) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID customerId;
        private Currency currency;

        public CreateOrderRequest build() {
            return new CreateOrderRequest(customerId, currency);
        }

        public Builder setCustomerId(UUID customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder setCurrency(Currency currency) {
            this.currency = currency;
            return this;
        }

    }

}
