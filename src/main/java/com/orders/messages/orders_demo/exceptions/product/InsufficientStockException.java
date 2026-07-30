package com.orders.messages.orders_demo.exceptions.product;

public class InsufficientStockException extends InvalidProductException {
    public InsufficientStockException() {
        super("Insufficient stock.");
    }

}
