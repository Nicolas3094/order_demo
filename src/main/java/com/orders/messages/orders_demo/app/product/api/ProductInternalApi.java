package com.orders.messages.orders_demo.app.product.api;

import com.orders.messages.orders_demo.app.product.internal.ProductEntity;

/**
 * Internal API for product management.
 */
public interface ProductInternalApi {

    /**
     * Retrieves a product by its SKU.
     *
     * @param sku the product SKU.
     * @return the requested product.
     */
    ProductEntity getProductBySku(String sku);

    /**
     * Saves a product.
     *
     * @param product the product to save.
     * @return the saved product.
     */
    ProductEntity saveProduct(ProductEntity product);

}
