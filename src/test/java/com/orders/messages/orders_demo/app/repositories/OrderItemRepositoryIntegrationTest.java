package com.orders.messages.orders_demo.app.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import com.orders.messages.orders_demo.app.PostgresContainer;

@Transactional
class OrderItemRepositoryIntegrationTest extends PostgresContainer {

    @Test
    void shouldPersistAndReloadItemWithAllMappedFields() {
    }

    @Test
    void shouldLoadAssociatedOrderWithinTransaction() {
    }

    @Test
    void shouldPersistUnitPriceChangeAndUpdatedLineTotal() {
    }

    @Test
    void shouldPersistQuantityChangeAndUpdatedLineTotal() {
    }

    @Test
    void shouldRejectPersistingItemWithoutOrder() {
    }

    @Test
    void shouldRejectPersistingItemReferencingMissingOrder() {
    }

    @Test
    void shouldRejectDescriptionExceedingColumnLength() {
    }
}
