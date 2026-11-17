package com.orders.messages.orders_demo.app.payment.internal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import com.orders.messages.orders_demo.app.PostgresContainer;

@AutoConfigureMockMvc
class PaymentControllerIntegrationTest extends PostgresContainer {

    @Test
    void shouldCreatePaymentThroughHttpAndReturn201WithPersistedOrderAmount() {
    }

    @Test
    void shouldReusePaymentThroughHttpForRepeatedIdempotencyKey() {
    }

    @Test
    void shouldListPersistedPaymentsThroughHttpAndReturn200WithJsonArray() {
    }

    @Test
    void shouldGetPersistedPaymentThroughHttpAndReturn200WithMatchingJson() {
    }

    @Test
    void shouldStartProcessingThroughHttpAndReturn200WithPersistedStatus() {
    }

    @Test
    void shouldMarkPaymentSucceededThroughHttpAndPersistPaymentAndOrderStatuses() {
    }

    @Test
    void shouldMarkPaymentFailedThroughHttpAndPersistFailureDetails() {
    }

    @Test
    void shouldCancelPaymentThroughHttpAndPersistCancelledStatus() {
    }

    @Test
    void shouldRejectInvalidPaymentRequestWith400AndErrorJsonWithoutPersistedChanges() {
    }

    @Test
    void shouldReturn404WithErrorJsonWhenPaymentDoesNotExist() {
    }

    @Test
    void shouldReturn409WithErrorJsonForInvalidPaymentTransition() {
    }
}
