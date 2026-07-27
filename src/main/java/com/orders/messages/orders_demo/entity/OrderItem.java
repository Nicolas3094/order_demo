package com.orders.messages.orders_demo.entity;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import com.orders.messages.orders_demo.exceptions.order_item.InvalidOrderItemStateException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_item_order"))
    private Order order;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private BigDecimal lineTotal;

    @Column(nullable = false)
    private Long quantity;

    protected OrderItem() {
    }

    private OrderItem(Builder builder) {
        this.id = builder.id;
        this.sku = builder.sku;
        this.description = builder.description;
        this.unitPrice = builder.unitPrice;
        this.quantity = builder.quantity;

        updateLineTotal();
    }

    void attachOrder(Order order) {
        this.order = order;
    }

    void detachOrder() {
        this.order = null;
    }

    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public String getSku() {
        return sku;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public Long getQuantity() {
        return quantity;
    }

    public void changeUnitPrice(BigDecimal unitPrice) {
        validateOrderCanModifyItems();

        this.unitPrice = Objects.requireNonNull(unitPrice);

        updateLineTotal();
    }

    public void changeQuantity(Long quantity) {
        validateOrderCanModifyItems();

        this.quantity = Objects.requireNonNull(quantity);

        updateLineTotal();
    }

    private void validateOrderCanModifyItems() {
        if (order == null) {
            throw new IllegalStateException("OrderItem is not attached to an Order.");
        }

        if (!order.canAcceptPayments()) {
            throw new InvalidOrderItemStateException(
                    "Only pending orders can modify items.");
        }
    }

    private void updateLineTotal() {
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID id;
        private String sku = "SKU";
        private String description = "";
        private BigDecimal unitPrice = BigDecimal.ZERO;
        private Long quantity = 1L;

        public OrderItem build() {
            return new OrderItem(this);
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder sku(String sku) {
            this.sku = Objects.requireNonNull(sku);

            if (sku.isBlank()) {
                throw new IllegalArgumentException("Sku cannot be blank.");
            }

            return this;
        }

        public Builder description(String description) {
            this.description = Objects.requireNonNull(description);
            return this;
        }

        public Builder unitPrice(BigDecimal unitPrice) {
            this.unitPrice = Objects.requireNonNull(unitPrice);
            return this;
        }

        public Builder quantity(Long quantity) {
            this.quantity = Objects.requireNonNull(quantity);
            return this;
        }

    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof OrderItem other))
            return false;
        return Objects.equals(id, other.id);
    }

}
