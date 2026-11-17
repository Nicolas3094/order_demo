package com.orders.messages.orders_demo.app.product.internal;

import org.junit.jupiter.api.Test;

import com.orders.messages.orders_demo.app.PostgresContainer;

class ProductServiceIntegrationTest extends PostgresContainer {

    @Test
    void shouldCreateAndPersistProduct() {
    }

    @Test
    void shouldRejectCreatingProductWithDuplicateSku() {
    }

    @Test
    void shouldGetAllPersistedProducts() {
    }

    @Test
    void shouldReturnEmptyListWhenNoProductsExist() {
    }

    @Test
    void shouldGetPersistedProductById() {
    }

    @Test
    void shouldRejectOperationsForMissingProduct() {
    }

    @Test
    void shouldChangePriceAndPersistProduct() {
    }

    @Test
    void shouldChangeCurrencyAndPersistProduct() {
    }

    @Test
    void shouldChangeNameAndPersistProduct() {
    }

    @Test
    void shouldChangeDescriptionAndPersistProduct() {
    }

    @Test
    void shouldIncreaseStockAndPersistQuantity() {
    }

    @Test
    void shouldDecreaseStockAndPersistQuantity() {
    }

    @Test
    void shouldRejectInsufficientStockWithoutChangingPersistedProduct() {
    }

    @Test
    void shouldActivateAndPersistProduct() {
    }

    @Test
    void shouldDeactivateAndPersistProduct() {
    }

    @Test
    void shouldDeletePersistedProduct() {
    }

    @Test
    void shouldRollBackProductCreationWhenCallerTransactionFails() {
    }

    @Test
    void shouldRollBackStockChangeWhenCallerTransactionFails() {
    }

    @Test
    void shouldRollBackProductUpdateWhenCallerTransactionFails() {
    }
}
