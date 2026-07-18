package com.orders.messages.orders_demo.exceptions.orders;

public class OrderNotFoundException extends InvalidOrderStateException {
    public OrderNotFoundException(String message){
        super(message);
    }

    public OrderNotFoundException() {
        super("Order could not be found.");
    }
}
