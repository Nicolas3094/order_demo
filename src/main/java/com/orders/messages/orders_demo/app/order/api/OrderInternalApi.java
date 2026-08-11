package com.orders.messages.orders_demo.app.order.api;

import java.util.UUID;

/**
 * Internal API for order management.
 */
public interface OrderInternalApi {

    /**
     * Validates that the order with the specified identifier exists.
     *
     * @param orderId the order identifier.
     * @throws OrderNotFoundException if the order does not exist.
     */
    void validateOrderExists(UUID orderId);

    /**
     * Retrieves the order data for the specified order identifier.
     *
     * @param orderId the order identifier.
     * @return the order data.
     * @throws OrderNotFoundException if the order does not exist.
     */
    void validateCanReceivePayment(UUID id);

    /**
     * Retrieves the order response for the specified order identifier.
     *
     * @param id the order identifier.
     * @return the order response.
     * @throws OrderNotFoundException if the order does not exist.
     */
    OrderResponse getOrderById(UUID id);

    /**
     * Marks the order with the specified identifier as paid.
     *
     * @param id the order identifier.
     * @throws OrderNotFoundException if the order does not exist.
     */
    void markAsPaid(UUID id);

    /**
     * Retrieves the order data for payment processing.
     *
     * @param orderId the order identifier.
     * @return the order data.
     * @throws OrderNotFoundException if the order does not exist.
     */
    OrderData getOrderDataForPayment(UUID orderId);

}