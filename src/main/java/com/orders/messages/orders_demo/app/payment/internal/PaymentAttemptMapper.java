package com.orders.messages.orders_demo.app.payment.internal;

import java.math.BigDecimal;
import java.util.UUID;

import com.orders.messages.orders_demo.app.payment.api.CreatePaymentAttemptRequest;
import com.orders.messages.orders_demo.app.payment.api.PaymentAttemptResponse;

public final class PaymentAttemptMapper {

    public static PaymentAttemptResponse toResponse(PaymentAttemptEntity paymentAttempt) {
        return PaymentAttemptResponse.builder()
                .setId(paymentAttempt.getId())
                .setProvider(paymentAttempt.getProvider())
                .setAmount(paymentAttempt.getAmount())
                .setStatus(paymentAttempt.getStatus())
                .setIdempotencyKey(paymentAttempt.getIdempotencyKey())
                .setProviderRef(paymentAttempt.getProviderRef())
                .setFailureMessage(paymentAttempt.getFailureMessage())
                .setFailureCode(paymentAttempt.getFailureCode())
                .setCreatedAt(paymentAttempt.getCreatedAt())
                .setUpdatedAt(paymentAttempt.getUpdatedAt())
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
