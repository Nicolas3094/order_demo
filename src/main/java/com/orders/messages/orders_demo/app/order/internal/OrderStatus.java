package com.orders.messages.orders_demo.app.order.internal;

/**
 * 
 * OrderStatus
 * 
 * Represents the lifecycle states of an order.
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    CANCELLED,
    EXPIRED,
    REFUNDED;
}
