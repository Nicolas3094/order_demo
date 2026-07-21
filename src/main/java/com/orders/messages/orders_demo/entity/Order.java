package com.orders.messages.orders_demo.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.orders.messages.orders_demo.enums.OrderStatus;
import com.orders.messages.orders_demo.exceptions.orders.InvalidOrderStateException;
import com.orders.messages.orders_demo.exceptions.orders.OrderAlreadyCancelledException;
import com.orders.messages.orders_demo.exceptions.orders.OrderAlreadyExpiredException;
import com.orders.messages.orders_demo.exceptions.orders.OrderAlreadyPaidException;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_customer"))
    private Customer customer;

    private String currency;

    private BigDecimal amountTotal;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Version
    private Long version;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    private Instant expiresAt;

    protected Order() {
    }

    public Order(Customer customer, String currency, BigDecimal amountTotal) {
        this.customer = customer;
        this.currency = currency;
        this.amountTotal = amountTotal;
        status = OrderStatus.PENDING_PAYMENT;
    }

    public UUID getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getAmountTotal() {
        return amountTotal;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void cancelOrder() {
        changeStatusFromPending(OrderStatus.CANCELLED);
    }

    public void markAsPaid() {
        changeStatusFromPending(OrderStatus.PAID);
    }

    public void expire() {
        changeStatusFromPending(OrderStatus.EXPIRED);
    }

    public void refund() {
        switch (status) {
            case CANCELLED -> throw new OrderAlreadyCancelledException();
            case PENDING_PAYMENT -> throw new InvalidOrderStateException(
                    "Only paid orders can be refunded.");
            case EXPIRED -> throw new OrderAlreadyExpiredException();
            case PAID -> status = OrderStatus.REFUNDED;
            default -> throw new InvalidOrderStateException("Unknown order state");
        }
    }

    private void changeStatusFromPending(OrderStatus newStatus) {
        switch (status) {
            case CANCELLED -> throw new OrderAlreadyCancelledException();
            case PENDING_PAYMENT -> status = newStatus;
            case EXPIRED -> throw new OrderAlreadyExpiredException();
            case PAID -> throw new OrderAlreadyPaidException();
            default -> throw new InvalidOrderStateException("Unknown order state");
        }
    }

}
