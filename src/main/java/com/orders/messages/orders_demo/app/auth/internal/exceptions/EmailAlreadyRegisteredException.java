package com.orders.messages.orders_demo.app.auth.internal.exceptions;

public class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException() {
        super("Email is already registered.");
    }
}
