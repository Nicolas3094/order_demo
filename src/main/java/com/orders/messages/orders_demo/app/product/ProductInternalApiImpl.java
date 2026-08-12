package com.orders.messages.orders_demo.app.product;

import org.springframework.stereotype.Service;

import com.orders.messages.orders_demo.app.product.api.ProductInternalApi;
import com.orders.messages.orders_demo.app.product.internal.ProductEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductRepository;
import com.orders.messages.orders_demo.app.product.internal.exceptions.ProductNotFoundException;

@Service
public class ProductInternalApiImpl implements ProductInternalApi {

    private final ProductRepository productRepository;

    public ProductInternalApiImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void increaceProductStock(String sku, long quantity) {
        ProductEntity product = productRepository.findBySku(sku).orElseThrow(ProductNotFoundException::new);

        product.increaseStock(quantity);

        productRepository.save(product);
    }

}
