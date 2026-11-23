package com.orders.messages.orders_demo.app.payment.internal;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.order.internal.OrderEntity;

public class PaymentAttemptEntityTest {

    private static final String DEFAULT_IDEMPOTENCY_KEY = "idempotency_key";
    private static final OrderEntity DEFAULT_ORDER = OrderEntity.builder()
            .id(UUID.randomUUID())
            .customerId(UUID.randomUUID())
            .currency(Currency.MXN)
            .build();

    @Test
    public void paymentAttemptConstructor_ShouldSetStatusAsCreated() {
        PaymentAttemptEntity result = PaymentAttemptEntity.builder()
                .orderId(DEFAULT_ORDER.getId())
                .provider(PaymentProvider.NONE)
                .idempotencyKey(DEFAULT_IDEMPOTENCY_KEY)
                .build();
        assertEquals(PaymentStatus.CREATED, result.getStatus());
    }

    @Test
    public void builder_WhenAllPropertiesAreProvided_ShouldSetAllProperties() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String providerRef = "provider_ref";
        String failureMessage = "Server error";
        Integer failureCode = 500;
        String idempotencyKey = "custom_key";
        PaymentStatus status = PaymentStatus.FAILED;

        PaymentAttemptEntity paymentAttempt = PaymentAttemptEntity.builder()
                .id(id)
                .orderId(orderId)
                .amount(new BigDecimal("150.50"))
                .provider(PaymentProvider.NONE)
                .idempotencyKey(idempotencyKey)
                .status(status)
                .providerRef(providerRef)
                .failureMessage(failureMessage)
                .failureCode(failureCode)
                .build();

        assertEquals(id, paymentAttempt.getId());
        assertEquals(orderId, paymentAttempt.getOrderId());
        assertEquals(new BigDecimal("150.50"), paymentAttempt.getAmount());
        assertEquals(PaymentProvider.NONE, paymentAttempt.getProvider());
        assertEquals(idempotencyKey, paymentAttempt.getIdempotencyKey());
        assertEquals(status, paymentAttempt.getStatus());
        assertEquals(providerRef, paymentAttempt.getProviderRef());
        assertEquals(failureMessage, paymentAttempt.getFailureMessage());
        assertEquals(failureCode, paymentAttempt.getFailureCode());
    }

    @Test
    public void builder_WhenOptionalPropertiesAreNotProvided_ShouldSetDefaultValues() {
        PaymentAttemptEntity paymentAttempt = PaymentAttemptEntity.builder()
                .orderId(DEFAULT_ORDER.getId())
                .build();

        assertEquals(BigDecimal.ZERO, paymentAttempt.getAmount());
        assertEquals(PaymentProvider.NONE, paymentAttempt.getProvider());
        assertEquals(PaymentStatus.CREATED, paymentAttempt.getStatus());
    }

    @SuppressWarnings("unused")
    private static Stream<Arguments> invalidPaymentStateExceptionWhenPaymentIsCancelled() {
        return Stream.of(
                Arguments.of(PaymentStatus.SUCCEEDED, "Successful payments cannot be cancelled."),
                Arguments.of(PaymentStatus.FAILED, "Failed payments cannot be cancelled."),
                Arguments.of(PaymentStatus.CANCELLED, "Payment is already cancelled."));
    }
}