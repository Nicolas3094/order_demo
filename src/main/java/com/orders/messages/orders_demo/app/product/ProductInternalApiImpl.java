package com.orders.messages.orders_demo.app.product;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orders.messages.orders_demo.app.product.api.ProductInternalApi;
import com.orders.messages.orders_demo.app.product.api.ProductResponse;
import com.orders.messages.orders_demo.app.product.internal.ProductEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductMapper;
import com.orders.messages.orders_demo.app.product.internal.ProductRepository;
import com.orders.messages.orders_demo.app.product.internal.exceptions.InsufficientStockException;
import com.orders.messages.orders_demo.app.product.internal.exceptions.InvalidProductException;
import com.orders.messages.orders_demo.app.product.internal.exceptions.ProductNotFoundException;

@Service
public class ProductInternalApiImpl implements ProductInternalApi {

    private final ProductRepository productRepository;

    private final EntityManager entityManager;

    public ProductInternalApiImpl(ProductRepository productRepository, EntityManager entityManager) {
        this.productRepository = productRepository;
        this.entityManager = entityManager;
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
    @Transactional
    public void decreaseProductStock(String sku, long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive.");
        }

        ProductEntity product = productRepository.findBySku(sku).orElseThrow(ProductNotFoundException::new);

        int updatedRows = productRepository.decreaseStockIfAvailable(product.getId(), quantity);

        if (updatedRows == 0) {
            throw new InsufficientStockException();
        }

        entityManager.refresh(product);
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
