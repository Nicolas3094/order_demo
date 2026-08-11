package com.orders.messages.orders_demo.app.payment.internal;

import java.math.BigDecimal;
import java.util.UUID;

import com.orders.messages.orders_demo.app.payment.api.CreatePaymentAttemptRequest;
import com.orders.messages.orders_demo.app.payment.api.PaymentAttemptResponse;

public final class PaymentAttemptMapper {

    public static PaymentAttemptResponse toResponse(PaymentAttemptEntity paymentAttempt) {
        return PaymentAttemptResponse.builder()
                .id(paymentAttempt.getId())
                .orderId(paymentAttempt.getOrderId())
                .provider(paymentAttempt.getProvider())
                .amount(paymentAttempt.getAmount())
                .status(paymentAttempt.getStatus())
                .idempotencyKey(paymentAttempt.getIdempotencyKey())
                .providerRef(paymentAttempt.getProviderRef())
                .failureMessage(paymentAttempt.getFailureMessage())
                .failureCode(paymentAttempt.getFailureCode())
                .createdAt(paymentAttempt.getCreatedAt())
                .updatedAt(paymentAttempt.getUpdatedAt())
                .build();
    }

    public static PaymentAttemptEntity toEntity(CreatePaymentAttemptRequest paymentAttemptRequest, BigDecimal amount,
            UUID orderId) {
        return PaymentAttemptEntity.builder()
                .orderId(orderId)
                .amount(amount)
                .provider(paymentAttemptRequest.provider())
                .idempotencyKey(paymentAttemptRequest.idempotencyKey())
                .build();
    }
}
