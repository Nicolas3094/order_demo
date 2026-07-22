package com.orders.messages.orders_demo.exceptions.customer;

public class CustomerBlockedException extends CustomerStateException {

    public CustomerBlockedException() {
        super("Blocked customer cannot be modified.");
    }

}
