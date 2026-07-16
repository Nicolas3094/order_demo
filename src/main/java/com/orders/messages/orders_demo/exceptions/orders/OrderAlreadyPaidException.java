package com.orders.messages.orders_demo.exceptions.orders;

public class OrderAlreadyPaidException extends InvalidOrderStateException {

    public OrderAlreadyPaidException() {
        super("Paid orders cannot be modified.");
    }

    public OrderAlreadyPaidException(String message) {
        super(message);
    }

}
