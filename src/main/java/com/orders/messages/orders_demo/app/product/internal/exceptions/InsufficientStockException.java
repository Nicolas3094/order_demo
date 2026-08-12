package com.orders.messages.orders_demo.app.product.internal.exceptions;

public class InsufficientStockException extends InvalidProductException {
    public InsufficientStockException() {
        super("Insufficient stock.");
    }
}
