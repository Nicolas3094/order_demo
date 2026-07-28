package com.orders.messages.orders_demo.dtos.request;

import java.math.BigDecimal;

import com.orders.messages.orders_demo.enums.Currency;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateProductRequest(
        @NotBlank(message = "SKU must not be blank.") String sku,
        @NotBlank(message = "Name must not be blank.") String name,
        @NotBlank(message = "Description must not be blank.") String description,
        @Positive(message = "Price must be positive.") BigDecimal price,
        @NotNull(message = "Currency is required.") Currency currency,
        boolean active,
        @PositiveOrZero(message = "Quantity must be zero or positive.") long quantity) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sku;
        private String name;
        private String description;
        private BigDecimal price;
        private boolean active;
        private long quantity;
        private Currency currency;

        public CreateProductRequest build() {
            return new CreateProductRequest(sku, name, description, price, currency, active, quantity);
        }

        public Builder currency(Currency currency) {
            this.currency = currency;
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

    }

}
