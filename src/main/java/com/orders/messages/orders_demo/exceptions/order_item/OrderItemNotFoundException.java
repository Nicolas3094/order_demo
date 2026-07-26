package com.orders.messages.orders_demo.exceptions.order_item;

public class OrderItemNotFoundException extends InvalidOrderItemStateException {

    public OrderItemNotFoundException() {
        super("Order item not found.");
    }

}
