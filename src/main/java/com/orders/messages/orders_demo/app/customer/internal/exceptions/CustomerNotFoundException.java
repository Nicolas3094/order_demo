package com.orders.messages.orders_demo.app.customer.internal.exceptions;

public class CustomerNotFoundException extends CustomerStateException {

    public CustomerNotFoundException() {
        super("Customer could not be found.");
    }

}
