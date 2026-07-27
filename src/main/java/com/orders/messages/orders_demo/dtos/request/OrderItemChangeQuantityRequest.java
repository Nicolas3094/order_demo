package com.orders.messages.orders_demo.dtos.request;

import jakarta.validation.constraints.Positive;

public record OrderItemChangeQuantityRequest(
        @Positive(message = "Quantity most be positive.") Long quanity) {

}
