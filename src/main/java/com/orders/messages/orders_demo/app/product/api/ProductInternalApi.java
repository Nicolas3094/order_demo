package com.orders.messages.orders_demo.app.product.api;

/**
 * Internal API for product management.
 */
public interface ProductInternalApi {

    /**
     * Increases the stock of a product by its SKU.
     *
     * @param sku      the product SKU.
     * @param quantity the quantity to increase.
     * @throws ProductNotFoundException if the product does not exist.
     */
    void increaceProductStock(String sku, long quantity);

}
