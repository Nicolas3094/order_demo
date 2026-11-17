package com.orders.messages.orders_demo.app.product.internal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import com.orders.messages.orders_demo.app.PostgresContainer;

@AutoConfigureMockMvc
class ProductControllerIntegrationTest extends PostgresContainer {

    @Test
    void shouldCreateProductThroughHttpAndReturn201WithPersistedData() {
    }

    @Test
    void shouldListPersistedProductsThroughHttpAndReturn200WithJsonArray() {
    }

    @Test
    void shouldGetPersistedProductThroughHttpAndReturn200WithMatchingJson() {
    }

    @Test
    void shouldChangePriceThroughHttpAndReturn200WithPersistedPrice() {
    }

    @Test
    void shouldIncreaseStockThroughHttpAndReturn200WithPersistedQuantity() {
    }

    @Test
    void shouldDecreaseStockThroughHttpAndReturn200WithPersistedQuantity() {
    }

    @Test
    void shouldChangeCurrencyThroughHttpAndReturn200WithPersistedCurrency() {
    }

    @Test
    void shouldChangeNameThroughHttpAndReturn200WithPersistedName() {
    }

    @Test
    void shouldChangeDescriptionThroughHttpAndReturn200WithPersistedDescription() {
    }

    @Test
    void shouldActivateProductThroughHttpAndReturn200WithPersistedState() {
    }

    @Test
    void shouldDeactivateProductThroughHttpAndReturn200WithPersistedState() {
    }

    @Test
    void shouldDeleteProductThroughHttpAndReturn204WithEmptyBodyAndPersistedDeletion() {
    }

    @Test
    void shouldRejectInvalidCreateRequestWith400AndErrorJsonWithoutPersistingProduct() {
    }

    @Test
    void shouldReturn404WithErrorJsonWhenProductDoesNotExist() {
    }

    @Test
    void shouldReturn409WithErrorJsonWhenCreatingProductWithDuplicateSku() {
    }
}
