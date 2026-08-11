package com.orders.messages.orders_demo.app.order_item.api;

import jakarta.validation.constraints.Positive;

public record OrderItemChangeQuantityRequest(
        @Positive(message = "Quantity most be positive.") Long quanity) {

}
