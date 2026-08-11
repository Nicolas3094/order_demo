package com.orders.messages.orders_demo.app.payment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.orders.messages.orders_demo.app.payment.internal.PaymentProvider;
import com.orders.messages.orders_demo.app.payment.internal.PaymentStatus;

public record PaymentAttemptResponse(
        UUID id,
        UUID orderId,
        PaymentProvider provider,
        BigDecimal amount,
        PaymentStatus status,
        String idempotencyKey,
        String providerRef,
        String failureMessage,
        Integer failureCode,
        Instant createdAt,
        Instant updatedAt) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID id;
        private UUID orderId;
        private PaymentProvider provider;
        private BigDecimal amount;
        private PaymentStatus status;
        private String idempotencyKey;
        private String providerRef;
        private String failureMessage;
        private Integer failureCode;
        private Instant createdAt;
        private Instant updatedAt;

        public PaymentAttemptResponse build() {
            return new PaymentAttemptResponse(id, orderId, provider, amount, status, idempotencyKey, providerRef,
                    failureMessage,
                    failureCode, createdAt, updatedAt);
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder orderId(UUID orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder provider(PaymentProvider provider) {
            this.provider = provider;
            return this;
        }

        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder status(PaymentStatus status) {
            this.status = status;
            return this;
        }

        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder providerRef(String providerRef) {
            this.providerRef = providerRef;
            return this;
        }

        public Builder failureMessage(String failureMessage) {
            this.failureMessage = failureMessage;
            return this;
        }

        public Builder failureCode(Integer failureCode) {
            this.failureCode = failureCode;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

    }

}
