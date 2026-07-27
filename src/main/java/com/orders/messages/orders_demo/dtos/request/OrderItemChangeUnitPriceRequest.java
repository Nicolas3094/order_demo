package com.orders.messages.orders_demo.dtos.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.Positive;

public record OrderItemChangeUnitPriceRequest(
        @Positive(message = "Unit pirce must be positive.") BigDecimal unitPrice) {

}
