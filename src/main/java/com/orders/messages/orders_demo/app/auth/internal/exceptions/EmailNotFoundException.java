package com.orders.messages.orders_demo.app.auth.internal.exceptions;

public class EmailNotFoundException extends RuntimeException {
    public EmailNotFoundException() {
        super("Email has not been found.");
    }
}
