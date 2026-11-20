package com.orders.messages.orders_demo.app.order.internal;

/**
 * 
 * OrderStatus
 * 
 * Represents the lifecycle states of an order.
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    PAYMENT_IN_PROGRESS,
    PAID,
    CANCELLED,
    EXPIRED,
    REFUNDED;
}
