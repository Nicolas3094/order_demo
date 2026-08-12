package com.orders.messages.orders_demo.app.product.api;

import com.orders.messages.orders_demo.app.product.internal.exceptions.InsufficientStockException;
import com.orders.messages.orders_demo.app.product.internal.exceptions.ProductNotFoundException;

/**
 * Internal API for product management.
 */
public interface ProductInternalApi {
    /**
     * Retrieves a product by its SKU.
     *
     * @param sku the product SKU.
     * @return the product response.
     * @throws ProductNotFoundException if the product does not exist.
     * 
     */
    ProductResponse getProductBySku(String sku);

    /**
     * Increases the stock of a product by its SKU.
     *
     * @param sku      the product SKU.
     * @param quantity the quantity to increase.
     * @throws ProductNotFoundException if the product does not exist.
     */
    void increaceProductStock(String sku, long quantity);

    /**
     * Decreases the stock of a product by its SKU.
     *
     * @param sku      the product SKU.
     * @param quantity the quantity to decrease.
     * @throws ProductNotFoundException   if the product does not exist.
     * @throws InsufficientStockException if there is not enough stock.
     */
    void decreaceProductStock(String sku, long quantity);

}
