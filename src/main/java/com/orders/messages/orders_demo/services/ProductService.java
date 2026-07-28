package com.orders.messages.orders_demo.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.orders.messages.orders_demo.dtos.request.CreateProductRequest;
import com.orders.messages.orders_demo.entity.Product;
import com.orders.messages.orders_demo.enums.Currency;
import com.orders.messages.orders_demo.exceptions.product.InvalidProductException;
import com.orders.messages.orders_demo.exceptions.product.ProductNotFoundException;
import com.orders.messages.orders_demo.mappers.ProductMapper;
import com.orders.messages.orders_demo.repositories.ProductRepository;

import jakarta.transaction.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product getProduct(UUID productId) {
        return findProduct(productId);
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Transactional
    public Product createProduct(CreateProductRequest request) {

        validateUniqueSku(request.sku());

        return productRepository.save(ProductMapper.toEntity(request));
    }

    @Transactional
    public Product changePrice(UUID productId, BigDecimal price) {
        return updateProduct(productId, product -> product.changePrice(price));
    }

    @Transactional
    public Product increaseStock(UUID productId, Long quantity) {
        return updateProduct(productId, product -> product.increaseStock(quantity));
    }

    @Transactional
    public Product decreaseStock(UUID productId, Long quantity) {
        return updateProduct(productId, product -> product.decreaseStock(quantity));
    }

    @Transactional
    public Product changeCurrency(UUID productId, Currency currency) {
        return updateProduct(productId, product -> product.changeCurrency(currency));
    }

    @Transactional
    public Product changeName(UUID productId, String name) {
        return updateProduct(productId, product -> product.changeName(name));
    }

    @Transactional
    public Product changeDescription(UUID productId, String description) {
        return updateProduct(productId, product -> product.changeDescription(description));
    }

    @Transactional
    public Product activate(UUID productId) {
        return updateProduct(productId, product -> product.activate());
    }

    @Transactional
    public Product deactivate(UUID productId) {
        return updateProduct(productId, product -> product.deactivate());
    }

    @Transactional
    public void deleteProduct(UUID productId) {
        Product product = findProduct(productId);

        productRepository.delete(product);
    }

    private void validateUniqueSku(String sku) {
        if (productRepository.existsBySku(sku)) {
            throw new InvalidProductException("Product must have unique SKU.");
        }
    }

    private Product updateProduct(UUID id, Consumer<Product> action) {
        Product product = findProduct(id);

        action.accept(product);

        return productRepository.save(product);
    }

    /**
     * Finds the Product if exits, otherwise throws an
     * {@link ProductNotFoundException}.
     * 
     * @param productId The Order ID.
     * @return A complete Order object.
     */
    private Product findProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(ProductNotFoundException::new);
    }

}
