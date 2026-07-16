package com.orders.messages.orders_demo.dtos.request;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request payload used to create a new order.
 *
 * @param customerId  identifier of the customer who owns the order.
 * @param currency    ISO 4217 currency code (e.g. MXN, USD).
 * @param amountTotal total monetary amount of the order.
 */
public record CreateOrderRequest(
        @NotNull UUID customerId,
        @NotBlank String currency,
        @Positive BigDecimal amountTotal) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID customerId;
        private String currency;
        private BigDecimal amountTotal;

        public CreateOrderRequest build() {
            return new CreateOrderRequest(customerId, currency, amountTotal);
        }

        public Builder setCustomerId(UUID customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder setCurrency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder setAmountTotal(BigDecimal amountTotal) {
            this.amountTotal = amountTotal;
            return this;
        }

    }

}
