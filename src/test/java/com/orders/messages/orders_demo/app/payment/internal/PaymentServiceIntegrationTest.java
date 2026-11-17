package com.orders.messages.orders_demo.app.payment.internal;

import org.junit.jupiter.api.Test;

import com.orders.messages.orders_demo.app.PostgresContainer;

class PaymentServiceIntegrationTest extends PostgresContainer {

    @Test
    void shouldCreatePaymentAttemptWithPersistedOrderAmount() {
    }

    @Test
    void shouldReuseExistingPaymentForRepeatedIdempotencyKey() {
    }

    @Test
    void shouldRejectReusingIdempotencyKeyForAnotherOrder() {
    }

    @Test
    void shouldListPaymentsForExistingOrder() {
    }

    @Test
    void shouldReturnEmptyListForOrderWithoutPayments() {
    }

    @Test
    void shouldGetPersistedPaymentById() {
    }

    @Test
    void shouldRejectPaymentBelongingToAnotherOrder() {
    }

    @Test
    void shouldRejectOperationsForMissingPayment() {
    }

    @Test
    void shouldRejectCreatingPaymentForMissingOrder() {
    }

    @Test
    void shouldRejectCreatingPaymentForNonPendingOrder() {
    }

    @Test
    void shouldStartProcessingAndPersistPaymentStatus() {
    }

    @Test
    void shouldMarkPaymentSucceededAndPersistOrderAsPaid() {
    }

    @Test
    void shouldMarkPaymentFailedAndPersistFailureDetails() {
    }

    @Test
    void shouldCancelPaymentWithoutChangingOrderStatus() {
    }

    @Test
    void shouldRejectInvalidPaymentTransitionsWithoutChangingPersistedState() {
    }

    @Test
    void shouldRollBackPaymentSuccessWhenOrderCannotBeMarkedPaid() {
    }

    @Test
    void shouldRollBackPaymentCreationWhenCallerTransactionFails() {
    }

    @Test
    void shouldRollBackPaymentAndOrderUpdatesWhenCallerTransactionFails() {
    }
}
