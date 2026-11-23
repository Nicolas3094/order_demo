package com.orders.messages.orders_demo.app.payment.internal;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.orders.messages.orders_demo.app.payment.internal.exceptions.InvalidPaymentStateException;

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

    @Test
    void startProcessing_WhenStateIsValid_ShouldApplyTransition() {
        var paymentAttempt = PaymentAttemptEntity.builder().orderId(DEFAULT_ORDER.getId())
                .status(PaymentStatus.CREATED).build();

        paymentAttempt.startProcessing();

        assertEquals(PaymentStatus.PROCESSING, paymentAttempt.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "CREATED")
    void startProcessing_WhenStateIsInvalid_ShouldRejectWithoutChangingState(PaymentStatus initialStatus) {
        var paymentAttempt = PaymentAttemptEntity.builder().orderId(DEFAULT_ORDER.getId()).status(initialStatus).build();

        var invalidPaymentStateException = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttempt.startProcessing());

        assertEquals("Only created payments can start processing.", invalidPaymentStateException.getMessage());
        assertEquals(initialStatus, paymentAttempt.getStatus());
    }

    @Test
    void markAsSucceeded_WhenStateIsValid_ShouldApplyTransition() {
        var paymentAttempt = PaymentAttemptEntity.builder().orderId(DEFAULT_ORDER.getId())
                .status(PaymentStatus.PROCESSING).build();

        paymentAttempt.markAsSucceeded("success-ref");

        assertEquals(PaymentStatus.SUCCEEDED, paymentAttempt.getStatus());
        assertEquals("success-ref", paymentAttempt.getProviderRef());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "PROCESSING")
    void markAsSucceeded_WhenStateIsInvalid_ShouldRejectWithoutChangingState(PaymentStatus initialStatus) {
        var paymentAttempt = PaymentAttemptEntity.builder().orderId(DEFAULT_ORDER.getId()).status(initialStatus).build();

        var invalidPaymentStateException = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttempt.markAsSucceeded("success-ref"));

        assertEquals("Only processing payments can be marked as succeeded.", invalidPaymentStateException.getMessage());
        assertEquals(initialStatus, paymentAttempt.getStatus());
    }

    @Test
    void markAsFailed_WhenStateIsValid_ShouldApplyTransition() {
        var paymentAttempt = PaymentAttemptEntity.builder().orderId(DEFAULT_ORDER.getId())
                .status(PaymentStatus.PROCESSING).build();

        paymentAttempt.markAsFailed(402, "Declined");

        assertEquals(PaymentStatus.FAILED, paymentAttempt.getStatus());
        assertEquals(402, paymentAttempt.getFailureCode());
        assertEquals("Declined", paymentAttempt.getFailureMessage());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "PROCESSING")
    void markAsFailed_WhenStateIsInvalid_ShouldRejectWithoutChangingState(PaymentStatus initialStatus) {
        var paymentAttempt = PaymentAttemptEntity.builder().orderId(DEFAULT_ORDER.getId()).status(initialStatus).build();

        var invalidPaymentStateException = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttempt.markAsFailed(402, "Declined"));

        assertEquals("Only processing payments can fail.", invalidPaymentStateException.getMessage());
        assertEquals(initialStatus, paymentAttempt.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = { "CREATED", "PROCESSING" })
    void cancel_WhenStateIsValid_ShouldCancelPayment(PaymentStatus initialStatus) {
        var paymentAttempt = PaymentAttemptEntity.builder().orderId(DEFAULT_ORDER.getId()).status(initialStatus).build();

        paymentAttempt.cancel();

        assertEquals(PaymentStatus.CANCELLED, paymentAttempt.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = { "SUCCEEDED", "FAILED", "CANCELLED" })
    void cancel_WhenStateIsTerminal_ShouldRejectWithoutChangingState(PaymentStatus initialStatus) {
        var paymentAttempt = PaymentAttemptEntity.builder().orderId(DEFAULT_ORDER.getId()).status(initialStatus).build();

        var invalidPaymentStateException = assertThrows(InvalidPaymentStateException.class, paymentAttempt::cancel);

        assertEquals("Only created or processing payments can be cancelled.", invalidPaymentStateException.getMessage());
        assertEquals(initialStatus, paymentAttempt.getStatus());
    }

}
