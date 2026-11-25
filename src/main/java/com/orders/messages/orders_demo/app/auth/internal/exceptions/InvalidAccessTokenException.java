package com.orders.messages.orders_demo.app.auth.internal.exceptions;

public class InvalidAccessTokenException extends RuntimeException {
    public InvalidAccessTokenException() {
        super("Invalid or expired access token.");
    }
}
