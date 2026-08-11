package com.orders.messages.orders_demo.app.customer.internal.exceptions;

public class CustomerBlockedException extends CustomerStateException {

    public CustomerBlockedException() {
        super("Blocked customer cannot be modified.");
    }

}
