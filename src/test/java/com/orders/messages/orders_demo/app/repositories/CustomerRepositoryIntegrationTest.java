package com.orders.messages.orders_demo.app.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import com.orders.messages.orders_demo.app.PostgresContainer;

@Transactional
class CustomerRepositoryIntegrationTest extends PostgresContainer {

    @Test
    void shouldPersistAndReloadCustomerWithAllMappedFields() {
    }

    @Test
    void shouldRejectDuplicateEmail() {
    }

    @Test
    void shouldPersistCustomerStatusChanges() {
    }

    @Test
    void shouldRejectNameExceedingColumnLength() {
    }

    @Test
    void shouldRejectEmailExceedingColumnLength() {
    }
}
