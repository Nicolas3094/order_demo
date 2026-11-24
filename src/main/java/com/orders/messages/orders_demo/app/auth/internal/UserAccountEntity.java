package com.orders.messages.orders_demo.app.auth.internal;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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
@Table(name = "user_account", uniqueConstraints = {
        @UniqueConstraint(name = "uk_email", columnNames = "email")
})
public class UserAccountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @CreationTimestamp
    private Instant createdAt;

    public static class Builder {
        private UUID id;
        private UUID customerId;
        private String email;
        private String passwordHash;
        private UserRole role = UserRole.CUSTOMER;

        public UserAccountEntity build() {
            if (customerId == null) {
                throw new IllegalStateException("Customer ID is required.");
            }
            if (email == null) {
                throw new IllegalStateException("Email is required.");
            }
            if (passwordHash == null) {
                throw new IllegalStateException("Password is required.");
            }
            return new UserAccountEntity(this);
        }

        public Builder id(UUID id) {
            this.id = Objects.requireNonNull(id);
            return this;
        }

        public Builder customerId(UUID customerId) {
            this.customerId = Objects.requireNonNull(customerId);

            return this;
        }

        public Builder email(String email) {
            this.email = Objects.requireNonNull(email);
            if (email.isBlank()) {
                throw new IllegalArgumentException("Email cannot be blank.");
            }
            return this;
        }

        public Builder passwordHash(String pasSwordHash) {
            this.passwordHash = Objects.requireNonNull(pasSwordHash);
            if (pasSwordHash.isBlank()) {
                throw new IllegalArgumentException("Password cannot be blank.");
            }
            return this;
        }

        public Builder role(UserRole role) {
            this.role = Objects.requireNonNull(role);
            return this;
        }

    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    private UserAccountEntity(Builder b) {
        this.id = b.id;
        this.customerId = b.customerId;
        this.email = b.email;
        this.passwordHash = b.passwordHash;
        this.role = b.role;
    }

    protected UserAccountEntity() {
    }

    @Override
    public int hashCode() {
        return UserAccountEntity.class.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof UserAccountEntity other))
            return false;
        if (this.id == other.id && other.id == null)
            return false;
        return Objects.equals(id, other.id);
    }

}
