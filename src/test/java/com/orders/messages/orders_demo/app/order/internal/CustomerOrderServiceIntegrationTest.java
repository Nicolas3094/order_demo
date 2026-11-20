package com.orders.messages.orders_demo.app.order.internal;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.orders.messages.orders_demo.app.PostgresContainer;

@Disabled("Pendiente de implementar: retirar al completar los tests")
public class CustomerOrderServiceIntegrationTest extends PostgresContainer {

    @Test
    public void shouldListOnlyRequestedCustomerOrdersAcrossStatusesWhenStatusIsNull() {
    }

    @Test
    public void shouldListOnlyOrdersMatchingCustomerAndStatus() {
    }

    @Test
    public void shouldReturnRequestedPageWithCorrectTotalsAndSorting() {
    }

    @Test
    public void shouldReturnEmptyPageWhenCustomerHasNoOrders() {
    }

    @Test
    public void shouldReturnEmptyPageWhenNoOrdersMatchStatus() {
    }

    @Test
    public void shouldReturnEmptyContentWithCorrectTotalsWhenPageIsOutOfRange() {
    }

    @Test
    public void shouldGetPersistedOrderBelongingToCustomerWithMappedFields() {
    }

    @Test
    public void shouldRejectGettingOrderBelongingToAnotherCustomer() {
    }

    @Test
    public void shouldRejectGettingMissingOrderForExistingCustomer() {
    }

    @Test
    public void shouldRejectGettingOrderForMissingCustomer() {
    }
}
