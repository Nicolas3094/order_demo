package com.orders.messages.orders_demo.app.payment.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.orders.messages.orders_demo.app.payment.internal.exceptions.InvalidPaymentStateException;

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
@Table(name = "payment_attempt", uniqueConstraints = {
        @UniqueConstraint(name = "uk_idempotency_key", columnNames = "idempotency_key")
})
public class PaymentAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider provider;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Column(nullable = false, length = 64)
    private String idempotencyKey;

    /** Gateway returning value */
    @Column(length = 100)
    private String providerRef;

    @Column(length = 500)
    private String failureMessage;

    private Integer failureCode;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected PaymentAttemptEntity() {
    }

    private PaymentAttemptEntity(Builder builder) {
        this.id = builder.id;
        this.orderId = builder.orderId;
        this.amount = builder.amount;
        this.provider = builder.provider;
        this.providerRef = builder.providerRef;
        this.failureMessage = builder.failureMessage;
        this.failureCode = builder.failureCode;
        this.idempotencyKey = builder.idempotencyKey;
        this.status = builder.status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
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
        switch (status) {
            case PROCESSING -> {
                this.providerRef = providerRef;
                status = PaymentStatus.SUCCEEDED;
            }
            default -> throw new InvalidPaymentStateException("Only processing payments can be marked as succeeded.");
        }
    }

    /** The payment failed. */
    public void markAsFailed(Integer failureCode, String failureMessage) {
        switch (status) {
            case PROCESSING -> {
                status = PaymentStatus.FAILED;
                this.failureCode = failureCode;
                this.failureMessage = failureMessage;
            }
            default -> throw new InvalidPaymentStateException("Only processing payments can fail.");
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

    public static final class Builder {
        private UUID id;
        private UUID orderId;
        private BigDecimal amount = BigDecimal.ZERO;
        private PaymentProvider provider = PaymentProvider.NONE;
        private String providerRef;
        private String failureMessage;
        private Integer failureCode;
        private String idempotencyKey = UUID.randomUUID().toString();
        private PaymentStatus status = PaymentStatus.CREATED;

        public Builder id(UUID id) {
            this.id = Objects.requireNonNull(id, "Payment ID must not be null.");
            return this;
        }

        public Builder orderId(UUID orderId) {
            this.orderId = Objects.requireNonNull(
                    orderId, "Order ID must not be null.");
            return this;
        }

        public Builder amount(BigDecimal amount) {
            this.amount = Objects.requireNonNull(
                    amount, "Amount must not be null.");

            if (amount.signum() != 1) {
                throw new IllegalArgumentException("Amount must be positive.");
            }

            return this;
        }

        public Builder provider(PaymentProvider provider) {
            this.provider = Objects.requireNonNull(provider, "Provider must not be null.");
            return this;
        }

        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "Idempotency key must not be null.");

            if (idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("Idempotency key cannot be blank.");
            }

            return this;
        }

        public Builder status(PaymentStatus status) {
            this.status = Objects.requireNonNull(status, "Status must not be null.");
            return this;
        }

        public Builder providerRef(String providerRef) {
            this.providerRef = Objects.requireNonNull(providerRef, "Provider reference must not be null.");

            if (providerRef.isBlank()) {
                throw new IllegalArgumentException("Provider reference cannot be blank.");
            }

            return this;
        }

        public Builder failureMessage(String failureMessage) {
            this.failureMessage = Objects.requireNonNull(failureMessage, "Failure message must not be null.");
            return this;
        }

        public Builder failureCode(Integer failureCode) {
            this.failureCode = Objects.requireNonNull(failureCode, "Failure code must not be null.");

            if (failureCode < 400 || failureCode > 599) {
                throw new IllegalArgumentException("Failure code must reference an HTTP error code.");
            }

            return this;
        }

        public PaymentAttemptEntity build() {
            return new PaymentAttemptEntity(this);
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof PaymentAttemptEntity other))
            return false;
        return Objects.equals(id, other.id);
    }

}
