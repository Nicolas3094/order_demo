package com.orders.messages.orders_demo.app.payment.api;

import jakarta.validation.constraints.NotEmpty;

public record PaymentFailedRequest(
        Integer code,
        @NotEmpty(message = "Error message must be sent.") String errorMessage) {

}
