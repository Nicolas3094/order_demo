package com.orders.messages.orders_demo.exceptions.customer;

public class CustomerNotFoundException extends CustomerStateException {

    public CustomerNotFoundException() {
        super("Customer could not be found.");
    }

}
