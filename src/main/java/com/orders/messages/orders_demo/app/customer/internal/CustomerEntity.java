package com.orders.messages.orders_demo.app.customer.internal;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerBlockedException;
import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerStateException;

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
@Table(name = "customer", uniqueConstraints = {
        @UniqueConstraint(name = "uk_customer_email", columnNames = "email")
})
public class CustomerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private CustomerStatus status;

    @CreationTimestamp
    private Instant createdAt;

    protected CustomerEntity() {
    }

    public CustomerEntity(String email, String name) {
        this.email = email;
        this.name = name;
        this.status = CustomerStatus.ACTIVE;
    }

    public CustomerEntity(UUID id, String email, String name, CustomerStatus status) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void deactivate() {
        if (CustomerStatus.ACTIVE.equals(status)) {
            this.status = CustomerStatus.BLOCKED;
        } else {
            throw new CustomerBlockedException();
        }
    }

    public void activate() {
        if (CustomerStatus.BLOCKED.equals(status)) {
            this.status = CustomerStatus.ACTIVE;
        } else {
            throw new CustomerStateException("Customer is already active.");
        }
    }
}
