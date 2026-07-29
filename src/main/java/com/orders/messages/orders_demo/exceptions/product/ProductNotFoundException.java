package com.orders.messages.orders_demo.exceptions.product;

public class ProductNotFoundException extends InvalidProductException {

    public ProductNotFoundException() {
        super("Product could not be found.");
    }

    public ProductNotFoundException(String sku) {
        super("Product with SKU " + sku + " could not be found.");
    }

}
