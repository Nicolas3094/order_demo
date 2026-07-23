package com.orders.messages.orders_demo.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.orders.messages.orders_demo.enums.PaymentProvider;
import com.orders.messages.orders_demo.enums.PaymentStatus;
import com.orders.messages.orders_demo.exceptions.payment.InvalidPaymentStateException;

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
    @Column(nullable = false)
    private PaymentProvider provider;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Column(nullable = false)
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

    public PaymentAttempt(Order order, PaymentProvider provider, String idempotencyKey, PaymentStatus status) {
        this.order = order;
        this.amount = order.getAmountTotal();
        this.provider = provider;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
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

    /** The Payment is under process. */
    public void startProcessing() {
        switch (status) {
            case CREATED -> status = PaymentStatus.PROCESSING;
            case PROCESSING -> throw new InvalidPaymentStateException("Payment is already being processed.");
            default -> throw new InvalidPaymentStateException("Only created payments can start processing.");
        }
    }

    /**
     * The money has already been collected.
     * 
     * @param providerRef The references sent by the provider.
     */
    public void markAsSucceeded(String providerRef) {
        if (PaymentStatus.PROCESSING.equals(status)) {
            this.providerRef = providerRef;
            status = PaymentStatus.SUCCEEDED;
        } else {
            throw new InvalidPaymentStateException("Only processing payments can be marked as succeeded.");
        }
    }

    /** The payment failed. */
    public void markAsFailed(Integer code, String message) {
        if (PaymentStatus.PROCESSING.equals(status)) {
            status = PaymentStatus.FAILED;
            this.failureCode = code;
            this.failureMessage = message;
        } else {
            throw new InvalidPaymentStateException("Only processing payments can fail.");
        }
    }

    /** The attempt was canceled before finishing. */
    public void cancel() {
        switch (status) {
            case CREATED, PROCESSING -> status = PaymentStatus.CANCELLED;
            case SUCCEEDED -> throw new InvalidPaymentStateException("Successful payments cannot be cancelled.");
            case FAILED -> throw new InvalidPaymentStateException("Failed payments cannot be cancelled.");
            case CANCELLED -> throw new InvalidPaymentStateException("Payment is already cancelled.");
            default -> throw new InvalidPaymentStateException("Unknown payment state.");
        }
    }

}
