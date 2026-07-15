package com.orders.messages.orders_demo.enums;

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
