package com.orders.messages.orders_demo.dtos.request;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request payload used to create a new order.
 *
 * @param customerId  identifier of the customer who owns the order.
 * @param currency    ISO 4217 currency code (e.g. MXN, USD).
 * @param amountTotal total monetary amount of the order.
 */
public record CreateOrderRequest(
        UUID customerId,
        String currency,
        BigDecimal amountTotal) {

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
