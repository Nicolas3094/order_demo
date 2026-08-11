package com.orders.messages.orders_demo.app.order.internal.exceptions;

public class OrderNotFoundException extends InvalidOrderStateException {
    public OrderNotFoundException(String message) {
        super(message);
    }

    public OrderNotFoundException() {
        super("Order could not be found.");
    }
}
