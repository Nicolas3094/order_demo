package com.orders.messages.orders_demo.app.order.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Represents the data of an order.
 */
public record OrderData(
        UUID orderId,
        BigDecimal amountTotal) {

}
