package com.orders.messages.orders_demo.dtos.response;

import java.math.BigDecimal;
import java.util.UUID;

import com.orders.messages.orders_demo.enums.Currency;

public record ProductResponse(
        UUID id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        Currency currency,
        boolean active,
        long quantity) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private String sku;
        private String name;
        private String description;
        private BigDecimal price;
        private boolean active;
        private long quantity;
        private Currency currency;

        public ProductResponse build() {
            return new ProductResponse(id, sku, name, description, price, currency, active, quantity);
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder sku(String sku) {
            this.sku = sku;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder price(BigDecimal price) {
            this.price = price;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public Builder quantity(long quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder currency(Currency currency) {
            this.currency = currency;
            return this;
        }
    }

}
