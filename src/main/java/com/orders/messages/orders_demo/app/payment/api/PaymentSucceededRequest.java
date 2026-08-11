package com.orders.messages.orders_demo.app.payment.api;

import jakarta.validation.constraints.NotEmpty;

public record PaymentSucceededRequest(
        @NotEmpty(message = "Provider reference must be sent.") String providerRef) {

}
