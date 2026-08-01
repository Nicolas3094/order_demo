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

    /**
     * Retrieves a product by its identifier.
     *
     * @param productId the product identifier.
     * @return the requested product.
     * @throws ProductNotFoundException if the product does not exist.
     */
    public Product getProduct(UUID productId) {
        return findProduct(productId);
    }

    /**
     * Retrieves all registered products.
     *
     * @return a list containing all products.
     */
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    /**
     * Creates a new product.
     *
     * @param request the product information used for creation.
     * @return the persisted product.
     * @throws InvalidProductException if another product already uses the same SKU.
     */
    @Transactional
    public Product createProduct(CreateProductRequest request) {

        validateUniqueSku(request.sku());

        return productRepository.save(ProductMapper.toEntity(request));
    }

    /**
     * Changes the price of a product.
     *
     * @param productId the product identifier.
     * @param price     the new product price.
     * @return the updated product.
     * @throws ProductNotFoundException if the product does not exist.
     */
    @Transactional
    public Product changePrice(UUID productId, BigDecimal price) {
        return updateProduct(productId, product -> product.changePrice(price));
    }

    /**
     * Increases the available stock of a product.
     *
     * @param productId the product identifier.
     * @param quantity  the quantity to add.
     * @return the updated product.
     * @throws ProductNotFoundException if the product does not exist.
     */
    @Transactional
    public Product increaseStock(UUID productId, Long quantity) {
        return updateProduct(productId, product -> product.increaseStock(quantity));
    }

    /**
     * Decreases the available stock of a product.
     *
     * @param productId the product identifier.
     * @param quantity  the quantity to remove.
     * @return the updated product.
     * @throws ProductNotFoundException if the product does not exist.
     * @throws InvalidProductException  if there is not enough stock available.
     */
    @Transactional
    public Product decreaseStock(UUID productId, Long quantity) {
        return updateProduct(productId, product -> product.decreaseStock(quantity));
    }

    /**
     * Changes the currency used by a product.
     *
     * @param productId the product identifier.
     * @param currency  the new product currency.
     * @return the updated product.
     * @throws ProductNotFoundException if the product does not exist.
     */
    @Transactional
    public Product changeCurrency(UUID productId, Currency currency) {
        return updateProduct(productId, product -> product.changeCurrency(currency));
    }

    /**
     * Changes the name of a product.
     *
     * @param productId the product identifier.
     * @param name      the new product name.
     * @return the updated product.
     * @throws ProductNotFoundException if the product does not exist.
     */
    @Transactional
    public Product changeName(UUID productId, String name) {
        return updateProduct(productId, product -> product.changeName(name));
    }

    /**
     * Changes the description of a product.
     *
     * @param productId   the product identifier.
     * @param description the new product description.
     * @return the updated product.
     * @throws ProductNotFoundException if the product does not exist.
     */
    @Transactional
    public Product changeDescription(UUID productId, String description) {
        return updateProduct(productId, product -> product.changeDescription(description));
    }

    /**
     * Activates a product.
     *
     * @param productId the product identifier.
     * @return the updated product.
     * @throws ProductNotFoundException if the product does not exist.
     */
    @Transactional
    public Product activate(UUID productId) {
        return updateProduct(productId, Product::activate);
    }

    /**
     * Deactivates a product.
     *
     * @param productId the product identifier.
     * @return the updated product.
     * @throws ProductNotFoundException if the product does not exist.
     */
    @Transactional
    public Product deactivate(UUID productId) {
        return updateProduct(productId, Product::deactivate);
    }

    /**
     * Deletes a product.
     *
     * @param productId the product identifier.
     * @throws ProductNotFoundException if the product does not exist.
     */
    @Transactional
    public void deleteProduct(UUID productId) {
        productRepository.delete(findProduct(productId));
    }

    /**
     * Validates that the given SKU is unique.
     *
     * @param sku the product SKU.
     * @throws InvalidProductException if another product already uses the same SKU.
     */
    private void validateUniqueSku(String sku) {
        if (productRepository.existsBySku(sku)) {
            throw new InvalidProductException("Product must have unique SKU.");
        }
    }

    /**
     * Applies the given update operation to a product and persists the changes.
     *
     * @param id     the product identifier.
     * @param action the update operation to apply.
     * @return the updated product.
     * @throws ProductNotFoundException if the product does not exist.
     */
    private Product updateProduct(UUID id, Consumer<Product> action) {
        Product product = findProduct(id);

        action.accept(product);

        return productRepository.save(product);
    }

    /**
     * Retrieves a product by its identifier.
     *
     * @param productId the product identifier.
     * @return the requested product.
     * @throws ProductNotFoundException if the product does not exist.
     */
    private Product findProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(ProductNotFoundException::new);
    }

}
