package com.orders.messages.orders_demo.app.customer.api;

import java.util.UUID;

import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerNotFoundException;

/**
 * Internal API for customer management.
 */
public interface CustomerInternalApi {

    /**
     * Retrieves a customer by its identifier.
     *
     * @param customerId the customer identifier.
     * @return the requested customer.
     * @throws CustomerNotFoundException if the customer does not exist.
     */
    CustomerResponse getCustomer(UUID customerId);

}
