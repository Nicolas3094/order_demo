package com.orders.messages.orders_demo.app.order.internal.exceptions;

public class OrderAlreadyCancelledException extends InvalidOrderStateException {

    public OrderAlreadyCancelledException() {
        super("Order is already cancelled.");
    }

}
