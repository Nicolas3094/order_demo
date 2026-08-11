package com.orders.messages.orders_demo.app.payment.internal;

import com.orders.messages.orders_demo.app.order.internal.OrderEntity;
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

    public static PaymentAttemptEntity toEntity(CreatePaymentAttemptRequest paymentAttemptRequest, OrderEntity order) {
        return new PaymentAttemptEntity(
                order,
                paymentAttemptRequest.provider(),
                paymentAttemptRequest.idempotencyKey());
    }
}
