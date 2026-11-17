package com.orders.messages.orders_demo.app.customer.internal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import com.orders.messages.orders_demo.app.PostgresContainer;

@AutoConfigureMockMvc
class CustomerControllerIntegrationTest extends PostgresContainer {

    @Test
    void shouldCreateCustomerThroughHttpAndReturn201WithPersistedData() {
    }

    @Test
    void shouldListPersistedCustomersThroughHttpAndReturn200WithJsonArray() {
    }

    @Test
    void shouldGetPersistedCustomerThroughHttpAndReturn200WithMatchingJson() {
    }

    @Test
    void shouldBlockCustomerThroughHttpAndReturn200WithPersistedStatus() {
    }

    @Test
    void shouldActivateCustomerThroughHttpAndReturn200WithPersistedStatus() {
    }

    @Test
    void shouldRejectInvalidCreateRequestWith400AndErrorJsonWithoutPersistingCustomer() {
    }

    @Test
    void shouldReturn404WithErrorJsonWhenCustomerDoesNotExist() {
    }

    @Test
    void shouldReturn409WithErrorJsonForInvalidCustomerStateTransition() {
    }
}
