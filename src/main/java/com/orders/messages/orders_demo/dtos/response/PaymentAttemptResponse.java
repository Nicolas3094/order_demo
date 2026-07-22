package com.orders.messages.orders_demo.dtos.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.orders.messages.orders_demo.enums.PaymentProvider;
import com.orders.messages.orders_demo.enums.PaymentStatus;

public record PaymentAttemptResponse(
        UUID id,
        PaymentProvider provider,
        BigDecimal amount,
        PaymentStatus status,
        String idempotencyKey,
        String providerRef,
        String failureMessage,
        Integer failureCode,
        Instant createdAt,
        Instant updatedAts) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID id;
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
            return new PaymentAttemptResponse(id, provider, amount, status, idempotencyKey, providerRef, failureMessage,
                    failureCode, createdAt, updatedAt);
        }

        public Builder setId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder setProvider(PaymentProvider provider) {
            this.provider = provider;
            return this;
        }

        public Builder setAmount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder setStatus(PaymentStatus status) {
            this.status = status;
            return this;
        }

        public Builder setIdempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder setProviderRef(String providerRef) {
            this.providerRef = providerRef;
            return this;
        }

        public Builder setFailureMessage(String failureMessage) {
            this.failureMessage = failureMessage;
            return this;
        }

        public Builder setFailureCode(Integer failureCode) {
            this.failureCode = failureCode;
            return this;
        }

        public Builder setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder setUpdatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

    }

}
