package com.orders.messages.orders_demo.exceptions.orders;

public class InvalidOrderStateException extends RuntimeException {
    public InvalidOrderStateException(String message) {
        super(message);
    }
}
