package com.orders.messages.orders_demo.exceptions.payment;

public class PaymentNotFoundException extends InvalidPaymentStateException {

    public PaymentNotFoundException() {
        super("Payment attempt not found.");
    }

}
