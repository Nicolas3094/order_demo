package com.orders.messages.orders_demo.app.order.internal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import com.orders.messages.orders_demo.app.PostgresContainer;

@AutoConfigureMockMvc
class OrderControllerIntegrationTest extends PostgresContainer {

    @Test
    void shouldCreateOrderThroughHttpAndReturn201WithPersistedCustomerCurrencyAndPendingStatus() {
    }

    @Test
    void shouldListPersistedOrdersThroughHttpAndReturn200WithJsonArray() {
    }

    @Test
    void shouldGetPersistedOrderThroughHttpAndReturn200WithMatchingJson() {
    }

    @Test
    void shouldCancelOrderThroughHttpAndReturn200WithPersistedStatusAndRestoredStock() {
    }

    @Test
    void shouldExpireOrderThroughHttpAndReturn200WithPersistedStatusAndRestoredStock() {
    }

    @Test
    void shouldRefundPaidOrderThroughHttpAndReturn200WithPersistedStatusAndRestoredStock() {
    }

    @Test
    void shouldRejectInvalidCreateRequestWith400AndErrorJsonWithoutPersistingOrder() {
    }

    @Test
    void shouldReturn404WithErrorJsonWhenRequestedOrderDoesNotExist() {
    }

    @Test
    void shouldReturn409WithErrorJsonWhenCancellingPaidOrderWithoutChangingPersistedState() {
    }
}
