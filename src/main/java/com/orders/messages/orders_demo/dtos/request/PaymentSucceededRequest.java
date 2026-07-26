package com.orders.messages.orders_demo.dtos.request;

import jakarta.validation.constraints.NotEmpty;

public record PaymentSucceededRequest(
        @NotEmpty(message = "Provider reference must be sent.") String providerRef) {

}
