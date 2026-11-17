package com.orders.messages.orders_demo.app.customer.internal;

import org.junit.jupiter.api.Test;

import com.orders.messages.orders_demo.app.PostgresContainer;

class CustomerServiceIntegrationTest extends PostgresContainer {

    @Test
    void shouldCreateAndPersistCustomer() {
    }

    @Test
    void shouldGetAllPersistedCustomers() {
    }

    @Test
    void shouldReturnEmptyListWhenNoCustomersExist() {
    }

    @Test
    void shouldGetPersistedCustomerById() {
    }

    @Test
    void shouldRejectGettingMissingCustomer() {
    }

    @Test
    void shouldRejectCreatingCustomerWithDuplicateEmail() {
    }

    @Test
    void shouldDeactivateAndPersistCustomerStatus() {
    }

    @Test
    void shouldActivateAndPersistCustomerStatus() {
    }

    @Test
    void shouldRejectInvalidCustomerStateTransitionsWithoutChangingPersistedState() {
    }

    @Test
    void shouldRejectStateChangesForMissingCustomer() {
    }

    @Test
    void shouldRollBackCustomerCreationWhenCallerTransactionFails() {
    }

    @Test
    void shouldRollBackCustomerStatusChangeWhenCallerTransactionFails() {
    }
}
