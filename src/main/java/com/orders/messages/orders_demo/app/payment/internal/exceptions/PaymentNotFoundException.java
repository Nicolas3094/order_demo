package com.orders.messages.orders_demo.app.payment.internal.exceptions;

public class PaymentNotFoundException extends InvalidPaymentStateException {

    public PaymentNotFoundException() {
        super("Payment attempt not found.");
    }

}
