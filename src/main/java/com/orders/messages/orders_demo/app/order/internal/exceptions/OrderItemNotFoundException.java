package com.orders.messages.orders_demo.app.order.internal.exceptions;

public class OrderItemNotFoundException extends InvalidOrderItemStateException {

    public OrderItemNotFoundException() {
        super("Order item not found.");
    }

}
