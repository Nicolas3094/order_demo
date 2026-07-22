package com.orders.messages.orders_demo.entity;

import java.math.BigDecimal;
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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "order_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_order_item_sku", columnNames = "sku")
})
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_item_order"))
    private Order order;

    @Column(nullable = false)
    private String sku;

    private String description;

    private BigDecimal unitPrice;

    private BigDecimal lineTotal;

    private Long quantity;

    protected OrderItem() {
    }

    public OrderItem(String sku, String description, BigDecimal unitPrice, Long quantity) {
        this.sku = sku;
        this.description = description;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        updateLineTotal();
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
        this.unitPrice = unitPrice;
        updateLineTotal();
    }

    public void changeQuantity(Long quantity) {
        this.quantity = quantity;
        updateLineTotal();
    }

    private void updateLineTotal() {
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

}
