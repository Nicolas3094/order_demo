package com.orders.messages.orders_demo.app.payment.internal;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.MethodSource;

import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderEntity;
import com.orders.messages.orders_demo.app.payment.internal.exceptions.InvalidPaymentStateException;

public class PaymentAttemptEntityTest {

    private static final String DEFAULT_IDEMPOTENCY_KEY = "idempotency_key";
    private static final CustomerEntity DEFAULT_CUSTOMER = new CustomerEntity("email", "name");
    private static final OrderEntity DEFAULT_ORDER = OrderEntity.builder()
            .id(UUID.randomUUID())
            .customer(DEFAULT_CUSTOMER)
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
    public void startProcessing_WhenStatusIsCreated_ShouldSetStatusAsProcessing() {
        PaymentAttemptEntity paymentAttempt = PaymentAttemptEntity.builder()
                .orderId(DEFAULT_ORDER.getId())
                .provider(PaymentProvider.NONE)
                .idempotencyKey(DEFAULT_IDEMPOTENCY_KEY)
                .build();

        paymentAttempt.startProcessing();

        assertEquals(PaymentStatus.PROCESSING, paymentAttempt.getStatus());
    }

    @Test
    public void startProcessing_WhenStatusIsProcessing_ShouldThrowInvalidPaymentStateException() {
        PaymentAttemptEntity paymentAttempt = createPaymentWithStatus(PaymentStatus.PROCESSING);

        InvalidPaymentStateException result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttempt.startProcessing());

        assertEquals("Payment is already being processed.", result.getMessage());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = { "SUCCEEDED", "FAILED", "CANCELLED" })
    public void startProcessing_WhenStatusIsOtherThanCreatedOrProcessing_ShouldThrowInvalidPaymentException(
            PaymentStatus status) {
        PaymentAttemptEntity paymentAttempt = createPaymentWithStatus(status);

        InvalidPaymentStateException result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttempt.startProcessing());

        assertEquals("Only created payments can start processing.", result.getMessage());
    }

    @Test
    public void markAsSucceeded_WhenStatusIsProcessing_ShouldSetProviderRefAndStatusAsSucceeded() {
        String providerRef = "provider_ref";
        PaymentAttemptEntity paymentAttempt = createPaymentWithStatus(PaymentStatus.PROCESSING);

        paymentAttempt.markAsSucceeded(providerRef);

        assertEquals(providerRef, paymentAttempt.getProviderRef());
        assertEquals(PaymentStatus.SUCCEEDED, paymentAttempt.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, mode = Mode.EXCLUDE, names = { "PROCESSING" })
    public void markAsSucceeded_WhenStatusIsOtherThanProcessing_ShouldThrowInvalidPaymentExceptionWithMessage(
            PaymentStatus status) {
        String providerRef = "provider_ref";
        PaymentAttemptEntity paymentAttempt = createPaymentWithStatus(status);

        InvalidPaymentStateException result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttempt.markAsSucceeded(providerRef));

        assertEquals("Only processing payments can be marked as succeeded.", result.getMessage());
    }

    @Test
    public void markAsFailed_WhenStatusIsProcessing_ShouldSetStatusAsFailedAndSetFailureCodeAndMessage() {
        Integer statusCode = 500;
        String message = "Server error";
        PaymentAttemptEntity paymentAttempt = createPaymentWithStatus(PaymentStatus.PROCESSING);

        paymentAttempt.markAsFailed(statusCode, message);

        assertEquals(PaymentStatus.FAILED, paymentAttempt.getStatus());
        assertEquals(statusCode, paymentAttempt.getFailureCode());
        assertEquals(message, paymentAttempt.getFailureMessage());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, mode = Mode.EXCLUDE, names = { "PROCESSING" })
    public void markAsFailed_WhenStatusIsOtherThanProcessing_ShouldThrowInvalidPaymentStateException(
            PaymentStatus status) {
        Integer statusCode = 500;
        String message = "Server error";
        PaymentAttemptEntity paymentAttempt = createPaymentWithStatus(status);

        InvalidPaymentStateException result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttempt.markAsFailed(statusCode, message));

        assertEquals("Only processing payments can fail.", result.getMessage());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = { "CREATED", "PROCESSING" })
    public void cancel_WhenStatusIsCreatedOrProcessing_ShouldSetStatusAsCancelled(PaymentStatus status) {
        PaymentAttemptEntity paymentAttempt = createPaymentWithStatus(status);

        paymentAttempt.cancel();

        assertEquals(PaymentStatus.CANCELLED, paymentAttempt.getStatus());
    }

    @ParameterizedTest
    @MethodSource("invalidPaymentStateExceptionWhenPaymentIsCancelled")
    public void cancel_WhenStatusIsOtherThanCreatedOrProcessing_ShouldThrowInvalidPaymentStateException(
            PaymentStatus status, String message) {
        PaymentAttemptEntity paymentAttempt = createPaymentWithStatus(status);

        InvalidPaymentStateException result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttempt.cancel());
        assertEquals(message, result.getMessage());
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

    private static PaymentAttemptEntity createPaymentWithStatus(PaymentStatus status) {
        return PaymentAttemptEntity.builder()
                .orderId(DEFAULT_ORDER.getId())
                .provider(PaymentProvider.NONE)
                .idempotencyKey(DEFAULT_IDEMPOTENCY_KEY)
                .status(status)
                .build();
    }

    @SuppressWarnings("unused")
    private static Stream<Arguments> invalidPaymentStateExceptionWhenPaymentIsCancelled() {
        return Stream.of(
                Arguments.of(PaymentStatus.SUCCEEDED, "Successful payments cannot be cancelled."),
                Arguments.of(PaymentStatus.FAILED, "Failed payments cannot be cancelled."),
                Arguments.of(PaymentStatus.CANCELLED, "Payment is already cancelled."));
    }
}