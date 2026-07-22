package com.orders.messages.orders_demo.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.orders.messages.orders_demo.enums.PaymentProvider;
import com.orders.messages.orders_demo.enums.PaymentStatus;

import jakarta.persistence.Column;
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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "payment_attempt", uniqueConstraints = {
        @UniqueConstraint(name = "uk_idempotency_key", columnNames = "idempotency_key")
})
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_payment_attempt_order"))
    private Order order;

    @Enumerated(EnumType.STRING)
    private PaymentProvider provider;

    private BigDecimal amount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String idempotencyKey;

    /** Gateway returning value */
    private String providerRef;

    private String failureMessage;

    private Integer failureCode;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected PaymentAttempt() {
    }

    public PaymentAttempt(Order order, PaymentProvider provider, String idempotencyKey) {
        this.order = order;
        this.amount = order.getAmountTotal();
        this.provider = provider;
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentStatus.CREATED;
    }

    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public Integer getFailureCode() {
        return failureCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

}
