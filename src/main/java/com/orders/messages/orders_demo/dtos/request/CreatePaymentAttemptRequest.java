package com.orders.messages.orders_demo.dtos.request;

import com.orders.messages.orders_demo.enums.PaymentProvider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload used to create a new payment attempt for an order.
 *
 * @param provider       provider where the payment was intended to be made.
 * @param idempotencyKey a unique identifier for a payment.
 */
public record CreatePaymentAttemptRequest(
        @NotNull(message = "Payment must have provide.") PaymentProvider provider,

        @NotBlank(message = "Payment must have idempotency key.") String idempotencyKey) {
}
