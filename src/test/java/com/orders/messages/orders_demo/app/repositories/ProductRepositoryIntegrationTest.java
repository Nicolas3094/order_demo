package com.orders.messages.orders_demo.app.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import com.orders.messages.orders_demo.app.PostgresContainer;

@Transactional
class ProductRepositoryIntegrationTest extends PostgresContainer {

    @Test
    void shouldPersistAndReloadProductWithAllMappedFields() {
    }

    @Test
    void shouldFindProductBySku() {
    }

    @Test
    void shouldReturnEmptyForUnknownSku() {
    }

    @Test
    void shouldReportExistingSku() {
    }

    @Test
    void shouldReportUnknownSkuAsAbsent() {
    }

    @Test
    void shouldRejectDuplicateSku() {
    }

    @Test
    void shouldPersistProductPriceCurrencyAndStockChanges() {
    }

    @Test
    void shouldPersistProductActivationChanges() {
    }

    @Test
    void shouldRejectSkuExceedingColumnLength() {
    }

    @Test
    void shouldRejectDescriptionExceedingColumnLength() {
    }
}
