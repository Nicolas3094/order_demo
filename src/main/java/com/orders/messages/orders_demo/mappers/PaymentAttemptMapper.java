package com.orders.messages.orders_demo.mappers;

import com.orders.messages.orders_demo.dtos.request.CreatePaymentAttemptRequest;
import com.orders.messages.orders_demo.dtos.response.PaymentAttemptResponse;
import com.orders.messages.orders_demo.entity.Order;
import com.orders.messages.orders_demo.entity.PaymentAttempt;

public final class PaymentAttemptMapper {

    public static PaymentAttemptResponse toResponse(PaymentAttempt paymentAttempt) {
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

    public static PaymentAttempt toEntity(CreatePaymentAttemptRequest paymentAttemptRequest, Order order) {
        return new PaymentAttempt(
                order,
                paymentAttemptRequest.provider(),
                paymentAttemptRequest.idempotencyKey());
    }
}
