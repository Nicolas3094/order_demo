package com.orders.messages.orders_demo.app.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import com.orders.messages.orders_demo.app.PostgresContainer;

@Transactional
class PaymentRepositoryIntegrationTest extends PostgresContainer {

    @Test
    void shouldPersistAndReloadPaymentAttemptWithAllMappedFields() {
    }

    @Test
    void shouldFindPaymentAttemptByIdempotencyKey() {
    }

    @Test
    void shouldReturnEmptyForUnknownIdempotencyKey() {
    }

    @Test
    void shouldFindOnlyPaymentsBelongingToRequestedOrder() {
    }

    @Test
    void shouldReturnEmptyListWhenOrderHasNoPayments() {
    }

    @Test
    void shouldRejectDuplicateIdempotencyKey() {
    }

    @Test
    void shouldPersistPaymentStatusAndProviderReference() {
    }

    @Test
    void shouldPersistPaymentFailureDetails() {
    }

    @Test
    void shouldRejectIdempotencyKeyExceedingColumnLength() {
    }

    @Test
    void shouldRejectProviderReferenceExceedingColumnLength() {
    }

    @Test
    void shouldRejectFailureMessageExceedingColumnLength() {
    }
}
