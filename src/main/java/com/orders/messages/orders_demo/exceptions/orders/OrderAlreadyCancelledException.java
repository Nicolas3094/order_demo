package com.orders.messages.orders_demo.exceptions.orders;

public class OrderAlreadyCancelledException extends InvalidOrderStateException {

    public OrderAlreadyCancelledException() {
        super("Order is already cancelled.");
    }

}
