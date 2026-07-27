package com.orders.messages.orders_demo.dtos.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload used to create a new order.
 *
 * @param customerId identifier of the customer who owns the order.
 * @param currency   ISO 4217 currency code (e.g. MXN, USD).
 */
public record CreateOrderRequest(
        @NotNull(message = "Customer id is required.") UUID customerId,
        @NotBlank(message = "Currency is required.") String currency) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID customerId;
        private String currency;

        public CreateOrderRequest build() {
            return new CreateOrderRequest(customerId, currency);
        }

        public Builder setCustomerId(UUID customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder setCurrency(String currency) {
            this.currency = currency;
            return this;
        }

    }

}
