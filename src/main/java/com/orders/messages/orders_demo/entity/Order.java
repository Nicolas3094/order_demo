package com.orders.messages.orders_demo.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.orders.messages.orders_demo.enums.Currency;
import com.orders.messages.orders_demo.enums.OrderStatus;
import com.orders.messages.orders_demo.exceptions.orders.InvalidOrderStateException;
import com.orders.messages.orders_demo.exceptions.orders.OrderAlreadyCancelledException;
import com.orders.messages.orders_demo.exceptions.orders.OrderAlreadyExpiredException;
import com.orders.messages.orders_demo.exceptions.orders.OrderAlreadyPaidException;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Column(nullable = false)
    private BigDecimal amountTotal;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Version
    private Long version;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    private Instant expiresAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    private Order(Builder builder) {
        this.id = builder.id;
        this.customer = builder.customer;
        this.currency = builder.currency;
        this.status = builder.status;

        this.items.addAll(builder.items);

        recalculateAmountTotal();
    }

    public UUID getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Currency getCurrency() {
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

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void addItem(OrderItem orderItem) {
        items.add(Objects.requireNonNull(orderItem));

        orderItem.attachOrder(this);

        recalculateAmountTotal();
    }

    public void removeItem(OrderItem orderItem) {
        items.remove(Objects.requireNonNull(orderItem));

        orderItem.detachOrder();

        recalculateAmountTotal();
    }

    private void recalculateAmountTotal() {
        amountTotal = BigDecimal.ZERO;

        for (OrderItem item : items) {
            amountTotal = amountTotal.add(item.getLineTotal());
        }
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

    public boolean canAcceptPayments() {
        return OrderStatus.PENDING_PAYMENT.equals(status);
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

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID id;
        private Customer customer;
        private Currency currency = Currency.MXN;
        private List<OrderItem> items = new ArrayList<>();
        private OrderStatus status = OrderStatus.PENDING_PAYMENT;

        public Order build() {
            if (customer == null) {
                throw new IllegalStateException("Customer is required.");
            }

            Order order = new Order(this);

            for (OrderItem item : order.items) {
                item.attachOrder(order);
            }

            return order;
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder customer(Customer customer) {
            this.customer = customer;
            return this;
        }

        public Builder currency(Currency currency) {
            this.currency = Objects.requireNonNull(currency, "Currency is required.");
            return this;
        }

        public Builder items(List<OrderItem> items) {
            this.items = new ArrayList<>(Objects.requireNonNull(items, "Item must not be null."));
            return this;
        }

        public Builder addItem(OrderItem item) {
            this.items.add(Objects.requireNonNull(item, "Item must not be null."));
            return this;
        }

        public Builder status(OrderStatus status) {
            this.status = Objects.requireNonNull(status, "Status must not be null.");
            return this;
        }

    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof Order other))
            return false;
        return Objects.equals(id, other.id);
    }

}
