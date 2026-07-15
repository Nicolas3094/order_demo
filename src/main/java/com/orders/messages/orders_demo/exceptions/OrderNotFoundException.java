package com.orders.messages.orders_demo.exceptions;

public class OrderNotFoundException extends InvalidOrderStateException {

    public OrderNotFoundException() {
        super("Order could not be found.");
    }

}
