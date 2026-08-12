package com.orders.messages.orders_demo.app.order.internal;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

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
public class OrderItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_item_order"))
    private OrderEntity order;

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

    protected OrderItemEntity() {
    }

    private OrderItemEntity(Builder builder) {
        this.id = builder.id;
        this.sku = builder.sku;
        this.description = builder.description;
        this.unitPrice = builder.unitPrice;
        this.quantity = builder.quantity;

        updateLineTotal();
    }

    public void attachOrder(OrderEntity order) {
        this.order = order;
    }

    public void detachOrder() {
        this.order = null;
    }

    public UUID getId() {
        return id;
    }

    public OrderEntity getOrder() {
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

        order.validateCanAcceptPayments();
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

        public OrderItemEntity build() {
            return new OrderItemEntity(this);
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder sku(String sku) {
            this.sku = Objects.requireNonNull(sku, "SKU cannot be null.");

            if (sku.isBlank()) {
                throw new IllegalArgumentException("Sku cannot be blank.");
            }

            return this;
        }

        public Builder description(String description) {
            this.description = Objects.requireNonNull(description, "Description cannot be null.");
            return this;
        }

        public Builder unitPrice(BigDecimal unitPrice) {
            this.unitPrice = Objects.requireNonNull(unitPrice, "Price cannot be null.");
            return this;
        }

        public Builder quantity(Long quantity) {
            this.quantity = Objects.requireNonNull(quantity, "Quantity cannot be null.");
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
        if (!(obj instanceof OrderItemEntity other))
            return false;
        return Objects.equals(id, other.id);
    }

}
