package com.orders.messages.orders_demo.dtos.response;

import java.time.Instant;
import java.util.UUID;

import com.orders.messages.orders_demo.enums.CustomerStatus;

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

        public Builder setId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder setEmail(String email) {
            this.email = email;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setStatus(CustomerStatus status) {
            this.status = status;
            return this;
        }

        public Builder setVersion(Long version) {
            this.version = version;
            return this;
        }

        public Builder setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

    }
}
