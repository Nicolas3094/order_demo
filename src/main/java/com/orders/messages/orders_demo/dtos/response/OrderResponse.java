package com.orders.messages.orders_demo.dtos.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.orders.messages.orders_demo.enums.OrderStatus;

/**
 * Response payload representing an order returned by the API.
 *
 * @param id          unique identifier of the order.
 * @param customerId  identifier of the customer who owns the order.
 * @param currency    ISO 4217 currency code.
 * @param amountTotal total monetary amount of the order.
 * @param status      current lifecycle status of the order.
 * @param version     optimistic locking version.
 * @param createdAt   timestamp when the order was created.
 * @param updatedAt   timestamp of the last update.
 * @param expiresAt   timestamp when the order expires, if applicable.
 */
public record OrderResponse(
        UUID id,
        UUID customerId,
        String currency,
        BigDecimal amountTotal,
        OrderStatus status,
        Long version,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private UUID customerId;
        private String currency;
        private BigDecimal amountTotal;
        private OrderStatus status;
        private Long version;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant expiresAt;

        public OrderResponse build() {
            return new OrderResponse(id, customerId, currency, amountTotal, status, version, createdAt, updatedAt,
                    expiresAt);
        }

        public Builder setId(UUID id) {
            this.id = id;
            return this;
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

        public Builder setStatus(OrderStatus status) {
            this.status = status;
            return this;
        }

        public Builder setVersion(Long version) {
            this.version = version;
            return this;
        }

        public Builder setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder setUpdatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder setExpiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

    }
}
