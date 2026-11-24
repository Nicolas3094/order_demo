package com.orders.messages.orders_demo.app.payment.internal;

public record PaymentProviderResult(PaymentProviderOutcome outcome, String providerRef,
        Integer failureCode, String failureMessage) {
}
