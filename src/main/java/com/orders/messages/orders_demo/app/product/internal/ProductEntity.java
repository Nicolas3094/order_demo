package com.orders.messages.orders_demo.app.product.internal;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.product.internal.exceptions.InsufficientStockException;
import com.orders.messages.orders_demo.app.product.internal.exceptions.InvalidProductException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "product", uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_sku", columnNames = "sku")
})
public class ProductEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private long quantity;

    protected ProductEntity() {
    }

    private ProductEntity(Builder builder) {
        this.id = builder.id;
        this.sku = builder.sku;
        this.name = builder.name;
        this.description = builder.description;
        this.price = builder.price;
        this.currency = builder.currency;
        this.active = builder.active;
        this.quantity = builder.quantity;
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Currency getCurrency() {
        return currency;
    }

    public boolean getActive() {
        return active;
    }

    public long getQuantity() {
        return quantity;
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    public void changePrice(BigDecimal price) {
        Objects.requireNonNull(price, "Price must not be null.");

        if (price.signum() != 1) {
            throw new IllegalArgumentException("Price must be positive.");
        }

        this.price = price;
    }

    public void changeName(String name) {
        if (!notBlank(name)) {
            throw new IllegalArgumentException("Name must not be empty.");
        }

        this.name = name;
    }

    public void changeDescription(String description) {
        if (!notBlank(description)) {
            throw new IllegalArgumentException("Description must not be empty.");
        }

        this.description = description;
    }

    public void changeCurrency(Currency currency) {
        this.currency = Objects.requireNonNull(currency, "Currency must not be null.");
    }

    /**
     * Increases the available stock for this product.
     *
     * @param quantity the quantity to add to stock.
     * @throws IllegalArgumentException if the quantity is zero or negative.
     */
    public void increaseStock(long quantity) {
        validatePositiveQuantity(quantity);

        this.quantity += quantity;
    }

    /**
     * Decreases the available stock for this product.
     *
     * @param quantity the quantity to subtract from stock.
     * @throws IllegalArgumentException   if the quantity is zero or negative.
     * @throws InsufficientStockException if there is not enough stock.
     */
    public void decreaseStock(long quantity) {
        validatePositiveQuantity(quantity);

        if (this.quantity < quantity) {
            throw new InsufficientStockException();
        }

        this.quantity -= quantity;
    }

    /**
     * Checks whether the product has enough stock for a requested quantity.
     *
     * @param quantity the quantity to validate.
     * @return true when stock is sufficient.
     * @throws IllegalArgumentException if the quantity is zero or negative.
     * @throws InvalidProductException  if there is not enough stock.
     */
    public boolean hasEnoughStock(long quantity) {
        validatePositiveQuantity(quantity);

        if (this.quantity < quantity) {
            throw new InvalidProductException("Product with SKU " + sku + " does not have enough stock.");
        }

        return true;
    }

    public static final class Builder {
        private UUID id;
        private String sku;
        private String name;
        private String description = "";
        private BigDecimal price;
        private Currency currency = Currency.MXN;
        private boolean active = true;
        private long quantity = 1L;

        public ProductEntity build() {

            Objects.requireNonNull(price, "Price must not be null.");
            Objects.requireNonNull(sku, "SKU must not be null.");
            Objects.requireNonNull(name, "Name must not be null.");

            return new ProductEntity(this);
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder sku(String sku) {
            if (!notBlank(sku)) {
                throw new IllegalArgumentException("SKU must not be empty.");
            }
            this.sku = sku;
            return this;
        }

        public Builder name(String name) {
            if (!notBlank(name)) {
                throw new IllegalArgumentException("Name must not be empty.");
            }
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            if (!notBlank(description)) {
                throw new IllegalArgumentException("Description must not be empty.");
            }
            this.description = description;
            return this;
        }

        public Builder price(BigDecimal price) {
            this.price = Objects.requireNonNull(price, "Price must not be null.");

            if (price.signum() != 1) {
                throw new IllegalArgumentException("Price must be positive.");
            }

            return this;
        }

        public Builder currency(Currency currency) {
            this.currency = Objects.requireNonNull(currency, "Currency must not be null.");
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public Builder quantity(long quantity) {
            if (quantity < 0) {
                throw new IllegalArgumentException("Quantity must be positive.");
            }
            this.quantity = quantity;
            return this;
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .currency(currency)
                .description(description)
                .name(name)
                .price(price)
                .sku(sku)
                .active(active)
                .quantity(quantity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof ProductEntity other))
            return false;
        return Objects.equals(id, other.id);
    }

    private static void validatePositiveQuantity(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive.");
        }
    }

    private static boolean notBlank(String str) {
        return str != null && !str.isBlank();
    }
}
