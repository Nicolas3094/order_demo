package com.orders.messages.orders_demo.app.order.api;

import com.orders.messages.orders_demo.app.order.internal.OrderEntity;

/**
 * Internal API for order management.
 */
public interface OrderInternalApi {

    /**
     * Retrieves an order by its identifier.
     *
     * @param id the order identifier.
     * @return the requested order.
     */
    OrderEntity getOrderById(java.util.UUID id);

    /**
     * Persists the provided order entity.
     *
     * @param order the order entity to be saved.
     * @return the persisted order entity.
     */
    OrderEntity saveOrder(OrderEntity order);

}
