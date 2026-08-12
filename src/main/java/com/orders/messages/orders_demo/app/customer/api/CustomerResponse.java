package com.orders.messages.orders_demo.app.customer.api;

import java.time.Instant;
import java.util.UUID;

import com.orders.messages.orders_demo.app.customer.internal.CustomerStatus;

/**
 * A record representing the response for a customer.
 */
public record CustomerResponse(UUID id,
        String email,
        String name,
        CustomerStatus status,
        Long version,
        Instant createdAt) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID id;
        private String email;
        private String name;
        private CustomerStatus status;
        private Long version;
        private Instant createdAt;

        public CustomerResponse build() {
            return new CustomerResponse(id, email, name, status, version, createdAt);
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder status(CustomerStatus status) {
            this.status = status;
            return this;
        }

        public Builder version(Long version) {
            this.version = version;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

    }
}
