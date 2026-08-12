package com.orders.messages.orders_demo.app.order_item.api;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID orderId,
        String sku,
        String description,
        BigDecimal unitPrice,
        Long quantity,
        BigDecimal lineTotal) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID id;
        private UUID orderId;
        private String sku;
        private String description;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
        private Long quantity;

        public OrderItemResponse build() {
            return new OrderItemResponse(id, orderId, sku, description, unitPrice, quantity, lineTotal);
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder orderId(UUID orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder sku(String sku) {
            this.sku = sku;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder unitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
            return this;
        }

        public Builder lineTotal(BigDecimal lineTotal) {
            this.lineTotal = lineTotal;
            return this;
        }

        public Builder quantity(Long quantity) {
            this.quantity = quantity;
            return this;
        }

    }

    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .lineTotal(lineTotal)
                .orderId(orderId)
                .quantity(quantity)
                .sku(sku)
                .unitPrice(unitPrice)
                .description(description);
    }
}
