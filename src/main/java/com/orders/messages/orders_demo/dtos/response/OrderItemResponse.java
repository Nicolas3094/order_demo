package com.orders.messages.orders_demo.dtos.response;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
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
        private String sku;
        private String description;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
        private Long quantity;

        public OrderItemResponse build() {
            return new OrderItemResponse(id, sku, description, unitPrice, quantity, lineTotal);
        }

        public Builder setId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder setSku(String sku) {
            this.sku = sku;
            return this;
        }

        public Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
            return this;
        }

        public Builder setLineTotal(BigDecimal lineTotal) {
            this.lineTotal = lineTotal;
            return this;
        }

        public Builder setQuantity(Long quantity) {
            this.quantity = quantity;
            return this;
        }

    }
}
