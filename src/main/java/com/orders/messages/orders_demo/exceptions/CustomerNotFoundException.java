package com.orders.messages.orders_demo.exceptions;

public class CustomerNotFoundException extends CustomerStateException {

    public CustomerNotFoundException() {
        super("Order could not be found.");
    }

}
