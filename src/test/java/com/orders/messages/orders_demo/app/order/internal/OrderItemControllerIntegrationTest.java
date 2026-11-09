package com.orders.messages.orders_demo.app.order.internal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import com.orders.messages.orders_demo.app.PostgresContainer;

@AutoConfigureMockMvc
class OrderItemControllerIntegrationTest extends PostgresContainer {

    @Test
    void shouldCreateItemThroughHttpAndReturn201WithPersistedItemStockAndOrderTotal() {
    }

    @Test
    void shouldListPersistedItemsThroughHttpAndReturn200WithJsonArray() {
    }

    @Test
    void shouldGetPersistedItemThroughHttpAndReturn200WithMatchingJson() {
    }

    @Test
    void shouldChangePriceThroughHttpAndReturn200WithPersistedTotals() {
    }

    @Test
    void shouldChangeQuantityThroughHttpAndReturn200WithPersistedStockAndTotals() {
    }

    @Test
    void shouldDeleteItemThroughHttpAndReturn204WithEmptyBodyAndPersistedChanges() {
    }

    @Test
    void shouldRejectInvalidCreateRequestWith400AndErrorJsonWithoutPersistedChanges() {
    }

    @Test
    void shouldReturn404WithErrorJsonWhenRequestedItemDoesNotExist() {
    }

    @Test
    void shouldReturn409WithErrorJsonWhenCreatingItemForNonPendingOrder() {
    }
}
