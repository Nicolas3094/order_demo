package com.orders.messages.orders_demo.entity;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.orders.messages.orders_demo.enums.PaymentProvider;
import com.orders.messages.orders_demo.enums.PaymentStatus;
import com.orders.messages.orders_demo.exceptions.payment.InvalidPaymentStateException;

public class PaymentAttemptTest {

    private static final String DEFAULT_IDEMPOTENCY_KEY = "idempotency_key";
    private static final Customer DEFAULT_CUSTOMER = new Customer("email", "name");
    private static final Order DEFAULT_ORDER = new Order(DEFAULT_CUSTOMER, "MXN", new BigDecimal("123.00"));

    @Test
    public void paymentAttemptConstructor_ShouldSetStatusAsCreated() {
        PaymentAttempt result = new PaymentAttempt(DEFAULT_ORDER, PaymentProvider.NONE, DEFAULT_IDEMPOTENCY_KEY);

        assertEquals(PaymentStatus.CREATED, result.getStatus());
    }

    @Test
    public void startProcessing_WhenStatusIsCreated_ShouldSetStatusAsProcessing() {
        PaymentAttempt paymentAttempt = new PaymentAttempt(
                DEFAULT_ORDER, PaymentProvider.NONE, DEFAULT_IDEMPOTENCY_KEY);

        paymentAttempt.startProcessing();

        assertEquals(PaymentStatus.PROCESSING, paymentAttempt.getStatus());
    }

    @Test
    public void startProcessing_WhenStatusIsProcessing_ShouldThrowInvalidPaymentException() {
        PaymentAttempt paymentAttempt = createPaymentWithStatus(PaymentStatus.PROCESSING);

        InvalidPaymentStateException result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttempt.startProcessing());

        assertEquals("Payment is already being processed.", result.getMessage());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = { "SUCCEEDED", "FAILED", "CANCELLED" })
    public void startProcessing_WhenStatusIsOtherThanCreatedOrProcessing_ShouldThrowInvalidPaymentException(
            PaymentStatus status) {
        PaymentAttempt paymentAttempt = createPaymentWithStatus(status);

        InvalidPaymentStateException result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttempt.startProcessing());

        assertEquals("Only created payments can start processing.", result.getMessage());
    }

    @Test
    public void markAsSucceeded_WhenStatusIsProcessing_ShouldSetProviderRefAndStatusAsSucceeded() {
        String providerRef = "provider_ref";
        PaymentAttempt paymentAttempt = createPaymentWithStatus(PaymentStatus.PROCESSING);

        paymentAttempt.markAsSucceeded(providerRef);

        assertEquals(providerRef, paymentAttempt.getProviderRef());
        assertEquals(PaymentStatus.SUCCEEDED, paymentAttempt.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = { "SUCCEEDED", "FAILED", "CANCELLED" })
    public void markAsSucceeded_WhenStatusIsOtherThanProcessing_ShouldThrowInvalidPaymentExceptionWithMessage(
            PaymentStatus status) {
        String providerRef = "provider_ref";
        PaymentAttempt paymentAttempt = createPaymentWithStatus(status);

        InvalidPaymentStateException result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttempt.markAsSucceeded(providerRef));

        assertEquals("Only processing payments can be marked as succeeded.", result.getMessage());
    }

    @Test
    public void markAsFailed_WhenStatusIsProcessing_ShouldSetStatusAsFailedAndSetFailureCodeAndMessage() {
        Integer statusCode = 500;
        String message = "Server error";
        PaymentAttempt paymentAttempt = createPaymentWithStatus(PaymentStatus.PROCESSING);

        paymentAttempt.markAsFailed(statusCode, message);

        assertEquals(PaymentStatus.FAILED, paymentAttempt.getStatus());
        assertEquals(statusCode, paymentAttempt.getFailureCode());
        assertEquals(message, paymentAttempt.getFailureMessage());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = { "CREATED", "SUCCEEDED", "FAILED", "CANCELLED" })
    public void markAsFailedWhenStatusIsOtherThanProcessing_ShouldThrowInvalidPaymentExceptionWithMessage(
            PaymentStatus status) {
        Integer statusCode = 500;
        String message = "Server error";
        PaymentAttempt paymentAttempt = createPaymentWithStatus(status);

        InvalidPaymentStateException result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttempt.markAsFailed(statusCode, message));

        assertEquals("Only processing payments can fail.", result.getMessage());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = { "CREATED", "PROCESSING" })
    public void cancel_WhenStatusIsCreatedOrProcessing_ShouldSetStatusAsCancelled(PaymentStatus status) {
        PaymentAttempt paymentAttempt = createPaymentWithStatus(status);

        paymentAttempt.cancel();

        assertEquals(PaymentStatus.CANCELLED, paymentAttempt.getStatus());
    }

    @ParameterizedTest
    @MethodSource("invalidPaymentStateExceptionWhenPaymentIsCancelled")
    public void cancel_WhenStatusIsOtherThanreatedOrProcessing_ShouldThrowInvalidPaymentExceptionWithMessage(
            PaymentStatus status, String message) {
        PaymentAttempt paymentAttempt = createPaymentWithStatus(status);

        InvalidPaymentStateException result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttempt.cancel());
        assertEquals(message, result.getMessage());
    }

    private static PaymentAttempt createPaymentWithStatus(PaymentStatus status) {
        return new PaymentAttempt(
                DEFAULT_ORDER, PaymentProvider.NONE, DEFAULT_IDEMPOTENCY_KEY, status);
    }

    private static Stream<Arguments> invalidPaymentStateExceptionWhenPaymentIsCancelled() {
        return Stream.of(
                Arguments.of(PaymentStatus.SUCCEEDED, "Successful payments cannot be cancelled."),
                Arguments.of(PaymentStatus.FAILED, "Failed payments cannot be cancelled."),
                Arguments.of(PaymentStatus.CANCELLED, "Payment is already cancelled."));
    }
}
