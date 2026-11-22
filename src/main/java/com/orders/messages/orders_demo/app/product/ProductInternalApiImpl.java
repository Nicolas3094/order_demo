package com.orders.messages.orders_demo.app.product;

import org.springframework.stereotype.Service;

import com.orders.messages.orders_demo.app.product.api.ProductInternalApi;
import com.orders.messages.orders_demo.app.product.api.ProductResponse;
import com.orders.messages.orders_demo.app.product.internal.ProductEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductMapper;
import com.orders.messages.orders_demo.app.product.internal.ProductRepository;
import com.orders.messages.orders_demo.app.product.internal.exceptions.InvalidProductException;
import com.orders.messages.orders_demo.app.product.internal.exceptions.ProductNotFoundException;

@Service
public class ProductInternalApiImpl implements ProductInternalApi {

    private final ProductRepository productRepository;

    public ProductInternalApiImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void increaseProductStock(String sku, long quantity) {
        ProductEntity product = productRepository.findBySku(sku).orElseThrow(ProductNotFoundException::new);

        product.increaseStock(quantity);

        productRepository.save(product);
    }

    @Override
    public ProductResponse getProductBySku(String sku) {
        return ProductMapper.toResponse(productRepository.findBySku(sku)
                .orElseThrow(ProductNotFoundException::new));
    }

    @Override
    public void decreaseProductStock(String sku, long quantity) {
        ProductEntity product = productRepository.findBySku(sku).orElseThrow(ProductNotFoundException::new);

        product.decreaseStock(quantity);

        productRepository.save(product);
    }

    @Override
    public void validateIsActiveProduct(ProductResponse productResponse) {
        if (!productResponse.active()) {
            throw new InvalidProductException(
                    "Product with SKU " + productResponse.sku() + " is not active.");
        }
    }

    @Override
    public void validateHasEnoughStock(ProductResponse productResponse, Long requestedQuantity) {
        if (productResponse.quantity() < requestedQuantity) {
            throw new InvalidProductException(
                    "Product with SKU " + productResponse.sku() + " has not enough stock.");
        }
    }

}
