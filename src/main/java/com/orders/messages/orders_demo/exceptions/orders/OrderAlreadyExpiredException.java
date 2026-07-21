package com.orders.messages.orders_demo.exceptions.orders;

public class OrderAlreadyExpiredException extends InvalidOrderStateException {

    public OrderAlreadyExpiredException() {
        super("Expired orders cannot be modified.");
    }

}
