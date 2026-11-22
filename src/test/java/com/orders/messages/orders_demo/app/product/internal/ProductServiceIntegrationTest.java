package com.orders.messages.orders_demo.app.product.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.product.api.CreateProductRequest;
import com.orders.messages.orders_demo.app.product.api.ProductResponse;
import com.orders.messages.orders_demo.app.product.internal.exceptions.*;

class ProductServiceIntegrationTest extends PostgresContainer {
    @Autowired
    private ProductService productService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<UUID> productIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            productIds.forEach(productRepository::deleteById);
            productRepository.flush();
        });
        productIds.clear();
    }

    @Test
    void shouldCreateAndPersistProduct() {
        var request = request(UUID.randomUUID().toString());

        var result = productService.createProduct(request);
        productIds.add(result.id());

        assertThat(result.id()).isNotNull();
        assertThat(result.sku()).isEqualTo(request.sku());
        var persisted = readProduct(result.id());
        assertThat(persisted.getSku()).isEqualTo(request.sku());
        assertThat(persisted.getName()).isEqualTo(request.name());
        assertThat(persisted.getDescription()).isEqualTo(request.description());
        assertThat(persisted.getPrice()).isEqualByComparingTo(request.price());
        assertThat(persisted.getCurrency()).isEqualTo(request.currency());
        assertThat(persisted.getQuantity()).isEqualTo(request.quantity());
        assertThat(persisted.getActive()).isEqualTo(request.active());
    }

    @Test
    void shouldRejectCreatingProductWithDuplicateSku() {
        UUID id = persistProduct(true);
        var original = readProduct(id);
        long initialCount = productRepository.count();

        assertThatThrownBy(() -> productService.createProduct(request(original.getSku())))
                .isInstanceOf(InvalidProductException.class).hasMessage("Product must have unique SKU.");

        assertThat(productRepository.count()).isEqualTo(initialCount);
        assertThat(readProduct(id).getName()).isEqualTo(original.getName());
    }

    @Test
    void shouldGetAllPersistedProducts() {
        UUID first = persistProduct(true);
        UUID second = persistProduct(false);

        var result = productService.getAllProducts();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ProductResponse::id).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void shouldReturnEmptyListWhenNoProductsExist() {
        assertThat(productService.getAllProducts()).isEmpty();
    }

    @Test
    void shouldGetPersistedProductById() {
        UUID id = persistProduct(false);
        var persisted = readProduct(id);

        var result = productService.getProduct(id);

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.sku()).isEqualTo(persisted.getSku());
        assertThat(result.name()).isEqualTo(persisted.getName());
        assertThat(result.description()).isEqualTo(persisted.getDescription());
        assertThat(result.price()).isEqualByComparingTo(persisted.getPrice());
        assertThat(result.currency()).isEqualTo(persisted.getCurrency());
        assertThat(result.quantity()).isEqualTo(persisted.getQuantity());
        assertThat(result.active()).isFalse();
    }

    @Test
    void shouldRejectOperationsForMissingProduct() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> productService.getProduct(id)).isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> productService.changePrice(id, new BigDecimal("200.00"))).isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> productService.changeCurrency(id, Currency.USD)).isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> productService.changeName(id, "New name")).isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> productService.changeDescription(id, "New description")).isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> productService.increaseStock(id, 2L)).isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> productService.decreaseStock(id, 2L)).isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> productService.activate(id)).isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> productService.deactivate(id)).isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> productService.deleteProduct(id)).isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void shouldChangePriceAndPersistProduct() {
        UUID id = persistProduct(true);

        var result = productService.changePrice(id, new BigDecimal("200.00"));

        assertThat(result.price()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(readProduct(id).getPrice()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    void shouldChangeCurrencyAndPersistProduct() {
        UUID id = persistProduct(true);

        var result = productService.changeCurrency(id, Currency.USD);

        assertThat(result.currency()).isEqualTo(Currency.USD);
        assertThat(readProduct(id).getCurrency()).isEqualTo(Currency.USD);
    }

    @Test
    void shouldChangeNameAndPersistProduct() {
        UUID id = persistProduct(true);

        var result = productService.changeName(id, "New name");

        assertThat(result.name()).isEqualTo("New name");
        assertThat(readProduct(id).getName()).isEqualTo("New name");
    }

    @Test
    void shouldChangeDescriptionAndPersistProduct() {
        UUID id = persistProduct(true);

        var result = productService.changeDescription(id, "New description");

        assertThat(result.description()).isEqualTo("New description");
        assertThat(readProduct(id).getDescription()).isEqualTo("New description");
    }

    @Test
    void shouldIncreaseStockAndPersistQuantity() {
        UUID id = persistProduct(true);

        var result = productService.increaseStock(id, 3L);

        assertThat(result.quantity()).isEqualTo(13L);
        assertThat(readProduct(id).getQuantity()).isEqualTo(13L);
    }

    @Test
    void shouldDecreaseStockAndPersistQuantity() {
        UUID id = persistProduct(true);

        var result = productService.decreaseStock(id, 3L);

        assertThat(result.quantity()).isEqualTo(7L);
        assertThat(readProduct(id).getQuantity()).isEqualTo(7L);
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, -1 })
    void shouldRejectNonPositiveStockDecreaseWithoutChangingProduct(long requestedQuantity) {
        UUID productId = persistProduct(true);

        assertThatThrownBy(() -> productService.decreaseStock(productId, requestedQuantity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Quantity must be positive.");

        assertThat(readProduct(productId).getQuantity()).isEqualTo(10L);
    }

    @Test
    void shouldKeepManagedProductSynchronizedAfterConsecutiveStockDecreases() {
        UUID productId = persistProduct(true);

        transactionTemplate.executeWithoutResult(status -> {
            ProductEntity managedProduct = productRepository.findById(productId).orElseThrow();

            var firstProductResponse = productService.decreaseStock(productId, 3L);
            var secondProductResponse = productService.decreaseStock(productId, 2L);

            assertThat(firstProductResponse.quantity()).isEqualTo(7L);
            assertThat(secondProductResponse.quantity()).isEqualTo(5L);
            assertThat(managedProduct.getQuantity()).isEqualTo(5L);
        });

        assertThat(readProduct(productId).getQuantity()).isEqualTo(5L);
    }

    @Test
    void shouldActivateAndPersistProduct() {
        UUID id = persistProduct(false);

        var result = productService.activate(id);

        assertThat(result.active()).isEqualTo(true);
        assertThat(readProduct(id).getActive()).isEqualTo(true);
    }

    @Test
    void shouldDeactivateAndPersistProduct() {
        UUID id = persistProduct(true);

        var result = productService.deactivate(id);

        assertThat(result.active()).isEqualTo(false);
        assertThat(readProduct(id).getActive()).isEqualTo(false);
    }

    @Test
    void shouldRejectInsufficientStockWithoutChangingPersistedProduct() {
        UUID id = persistProduct(true);

        assertThatThrownBy(() -> productService.decreaseStock(id, 11L))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(readProduct(id).getQuantity()).isEqualTo(10L);
    }

    @Test
    void shouldDeletePersistedProduct() {
        UUID id = persistProduct(true);

        productService.deleteProduct(id);

        assertThat(productRepository.findById(id)).isEmpty();
    }

    @Test
    void shouldRollBackProductCreationWhenCallerTransactionFails() {
        String sku = UUID.randomUUID().toString();
        long initialCount = productRepository.count();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            var result = productService.createProduct(request(sku));
            productIds.add(result.id());
            entityManager.flush();
            entityManager.clear();
            assertThat(productRepository.findById(result.id())).isPresent();
            throw new IllegalStateException("Caller failed.");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Caller failed.");

        assertThat(productRepository.findBySku(sku)).isEmpty();
        assertThat(productRepository.count()).isEqualTo(initialCount);
    }

    @Test
    void shouldRollBackStockChangeWhenCallerTransactionFails() {
        UUID id = persistProduct(true);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            productService.decreaseStock(id, 3L);
            entityManager.flush();
            entityManager.clear();
            assertThat(productRepository.findById(id).orElseThrow().getQuantity()).isEqualTo(7L);
            throw new IllegalStateException("Caller failed.");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Caller failed.");

        assertThat(readProduct(id).getQuantity()).isEqualTo(10L);
    }

    @Test
    void shouldRollBackProductUpdateWhenCallerTransactionFails() {
        UUID id = persistProduct(true);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            productService.changePrice(id, new BigDecimal("200.00"));
            productService.changeName(id, "New name");
            entityManager.flush();
            entityManager.clear();
            var updated = productRepository.findById(id).orElseThrow();
            assertThat(updated.getPrice()).isEqualByComparingTo("200.00");
            assertThat(updated.getName()).isEqualTo("New name");
            throw new IllegalStateException("Caller failed.");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Caller failed.");

        var persisted = readProduct(id);
        assertThat(persisted.getPrice()).isEqualByComparingTo("123.50");
        assertThat(persisted.getName()).isEqualTo("Test Product");
    }

    private CreateProductRequest request(String sku) {
        return new CreateProductRequest(sku, "Test Product", "Test description",
                new BigDecimal("123.50"), Currency.MXN, true, 10L);
    }

    private UUID persistProduct(boolean active) {
        return transactionTemplate.execute(status -> {
            UUID id = productRepository.saveAndFlush(ProductEntity.builder()
                    .sku(UUID.randomUUID().toString()).name("Test Product").description("Test description")
                    .price(new BigDecimal("123.50")).currency(Currency.MXN).quantity(10L).active(active)
                    .build()).getId();
            productIds.add(id);
            return id;
        });
    }

    private ProductEntity readProduct(UUID id) {
        return transactionTemplate.execute(status -> productRepository.findById(id).orElseThrow());
    }
}
