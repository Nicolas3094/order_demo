package com.orders.messages.orders_demo.app.order.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.order.internal.exceptions.InvalidOrderStateException;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderAlreadyCancelledException;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderAlreadyExpiredException;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderAlreadyPaidException;
import com.orders.messages.orders_demo.app.order_item.internal.OrderItemEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

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
    private List<OrderItemEntity> items = new ArrayList<>();

    protected OrderEntity() {
    }

    private OrderEntity(Builder builder) {
        this.id = builder.id;
        this.customerId = builder.customerId;
        this.currency = builder.currency;
        this.status = builder.status;

        this.items.addAll(builder.items);

        recalculateAmountTotal();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
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

    public List<OrderItemEntity> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void addItem(OrderItemEntity orderItem) {
        items.add(Objects.requireNonNull(orderItem));

        orderItem.attachOrder(this);

        recalculateAmountTotal();
    }

    public void removeItem(OrderItemEntity orderItem) {
        items.remove(Objects.requireNonNull(orderItem));

        orderItem.detachOrder();

        recalculateAmountTotal();
    }

    private void recalculateAmountTotal() {
        amountTotal = BigDecimal.ZERO;

        for (OrderItemEntity item : items) {
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

    public boolean validateCurrency(ProductEntity product) {
        if (!this.currency.equals(product.getCurrency())) {
            throw new InvalidOrderStateException("Product currency " + product.getCurrency()
                    + " does not match order currency " + this.currency + ".");
        }
        return true;
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

    public static final class Builder {
        private UUID id;
        private UUID customerId;
        private Currency currency = Currency.MXN;
        private List<OrderItemEntity> items = new ArrayList<>();
        private OrderStatus status = OrderStatus.PENDING_PAYMENT;

        public OrderEntity build() {
            if (customerId == null) {
                throw new IllegalStateException("Customer ID is required.");
            }

            OrderEntity order = new OrderEntity(this);

            for (OrderItemEntity item : order.items) {
                item.attachOrder(order);
            }

            return order;
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder customerId(UUID customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder currency(Currency currency) {
            this.currency = Objects.requireNonNull(currency, "Currency is required.");
            return this;
        }

        public Builder items(List<OrderItemEntity> items) {
            this.items = new ArrayList<>(Objects.requireNonNull(items, "Item must not be null."));
            return this;
        }

        public Builder addItem(OrderItemEntity item) {
            this.items.add(Objects.requireNonNull(item, "Item must not be null."));
            return this;
        }

        public Builder status(OrderStatus status) {
            this.status = Objects.requireNonNull(status, "Status must not be null.");
            return this;
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
        if (!(obj instanceof OrderEntity other))
            return false;
        return Objects.equals(id, other.id);
    }

}
