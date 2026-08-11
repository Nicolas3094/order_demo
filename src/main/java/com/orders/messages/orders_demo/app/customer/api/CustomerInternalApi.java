package com.orders.messages.orders_demo.app.customer.api;

import java.util.UUID;

import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;

/**
 * Internal API for customer management.
 */
public interface CustomerInternalApi {

    /**
     * Retrieves a customer by its identifier.
     *
     * @param id the customer identifier.
     * @return the requested customer.
     */
    CustomerEntity getCustomer(UUID id);

}
