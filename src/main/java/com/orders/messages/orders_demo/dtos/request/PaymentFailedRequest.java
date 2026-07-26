package com.orders.messages.orders_demo.dtos.request;

import jakarta.validation.constraints.NotEmpty;

public record PaymentFailedRequest(
        Integer code,
        @NotEmpty(message = "Error message must be sent.") String errorMessage) {

}
