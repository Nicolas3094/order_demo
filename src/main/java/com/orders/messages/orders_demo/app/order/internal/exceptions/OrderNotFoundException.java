package com.orders.messages.orders_demo.app.order.internal.exceptions;

import java.util.UUID;

public class OrderNotFoundException extends InvalidOrderStateException {
    public OrderNotFoundException(String message) {
        super(message);
    }

    public OrderNotFoundException() {
        super("Order could not be found.");
    }

    public OrderNotFoundException(UUID orderId) {
        super("Order with id " + orderId + " not found.");
    }
}
