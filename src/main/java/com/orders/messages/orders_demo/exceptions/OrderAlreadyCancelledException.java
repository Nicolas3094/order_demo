package com.orders.messages.orders_demo.exceptions;

public class OrderAlreadyCancelledException extends InvalidOrderStateException {

    public OrderAlreadyCancelledException() {
        super("Order is already cancelled.");
    }

}
