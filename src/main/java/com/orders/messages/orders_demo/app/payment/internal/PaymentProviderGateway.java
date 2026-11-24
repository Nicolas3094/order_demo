package com.orders.messages.orders_demo.app.payment.internal;

/** Read-only lookup; must not initiate or retry a charge. */
public interface PaymentProviderGateway {
    PaymentProviderResult lookupPayment(PaymentProvider provider, String idempotencyKey, String providerRef);
}
