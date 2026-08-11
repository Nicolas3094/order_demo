package com.orders.messages.orders_demo.app.order.internal.exceptions;

public class OrderAlreadyExpiredException extends InvalidOrderStateException {

    public OrderAlreadyExpiredException() {
        super("Expired orders cannot be modified.");
    }

}
