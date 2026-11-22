package com.orders.messages.orders_demo.app.repositories;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.product.internal.ProductEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductRepository;

@Transactional
class ProductRepositoryIntegrationTest extends PostgresContainer {
    @Autowired
    private ProductRepository productRepository;

    @Test
    void shouldPersistAndReloadProductWithAllMappedFields() {
        ProductEntity saved = productRepository.saveAndFlush(createProduct());
        entityManager.clear();

        ProductEntity result = productRepository.findById(saved.getId()).orElseThrow();

        assertThat(result).isNotSameAs(saved);
        assertThat(result.getId()).isEqualTo(saved.getId());
        assertThat(result.getSku()).isEqualTo(saved.getSku());
        assertThat(result.getName()).isEqualTo("Test Product");
        assertThat(result.getDescription()).isEqualTo("Test description");
        assertThat(result.getPrice()).isEqualByComparingTo("123.50");
        assertThat(result.getCurrency()).isEqualTo(Currency.MXN);
        assertThat(result.getQuantity()).isEqualTo(10L);
        assertThat(result.getActive()).isTrue();
    }

    @Test
    void shouldFindProductBySku() {
        ProductEntity saved = productRepository.saveAndFlush(createProduct());
        productRepository.saveAndFlush(createProduct());
        entityManager.clear();

        var result = productRepository.findBySku(saved.getSku());

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getId()).isEqualTo(saved.getId());
    }

    @Test
    void shouldReturnEmptyForUnknownSku() {
        productRepository.saveAndFlush(createProduct());
        entityManager.clear();

        assertThat(productRepository.findBySku(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    void shouldReportExistingSku() {
        ProductEntity saved = productRepository.saveAndFlush(createProduct());
        entityManager.clear();

        assertThat(productRepository.existsBySku(saved.getSku())).isTrue();
    }

    @Test
    void shouldReportUnknownSkuAsAbsent() {
        productRepository.saveAndFlush(createProduct());
        entityManager.clear();

        assertThat(productRepository.existsBySku(UUID.randomUUID().toString())).isFalse();
    }

    @Test
    void shouldRejectDuplicateSku() {
        ProductEntity saved = productRepository.saveAndFlush(createProduct());
        entityManager.clear();
        ProductEntity duplicate = createProduct(saved.getSku(), "Another description");

        assertThatThrownBy(() -> productRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("uk_product_sku");
    }

    @Test
    void shouldPersistProductPriceCurrencyAndStockChanges() {
        UUID id = productRepository.saveAndFlush(createProduct()).getId();
        entityManager.clear();
        ProductEntity product = productRepository.findById(id).orElseThrow();

        product.changePrice(new BigDecimal("200.00"));
        product.changeCurrency(Currency.USD);
        productRepository.saveAndFlush(product);
        entityManager.clear();

        assertThat(productRepository.increaseStockIfAvailable(id, 3L)).isEqualTo(1);
        entityManager.clear();

        ProductEntity updated = productRepository.findById(id).orElseThrow();
        assertThat(updated.getPrice()).isEqualByComparingTo("200.00");
        assertThat(updated.getCurrency()).isEqualTo(Currency.USD);
        assertThat(updated.getQuantity()).isEqualTo(13L);

        productRepository.decreaseStockIfAvailable(id, 5L);
        entityManager.clear();

        assertThat(productRepository.findById(id).orElseThrow().getQuantity()).isEqualTo(8L);
    }

    @Test
    void shouldPersistProductActivationChanges() {
        UUID id = productRepository.saveAndFlush(createProduct()).getId();
        entityManager.clear();
        ProductEntity product = productRepository.findById(id).orElseThrow();

        product.deactivate();
        productRepository.saveAndFlush(product);
        entityManager.clear();

        ProductEntity inactive = productRepository.findById(id).orElseThrow();
        assertThat(inactive.getActive()).isFalse();

        inactive.activate();
        productRepository.saveAndFlush(inactive);
        entityManager.clear();

        assertThat(productRepository.findById(id).orElseThrow().getActive()).isTrue();
    }

    @Test
    void shouldRejectSkuExceedingColumnLength() {
        productRepository.saveAndFlush(createProduct("S".repeat(64), "Test description"));
        entityManager.clear();

        assertThatThrownBy(() -> productRepository.saveAndFlush(createProduct("S".repeat(65), "Test description")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("value too long for type character varying(64)");
    }

    @Test
    void shouldRejectDescriptionExceedingColumnLength() {
        productRepository.saveAndFlush(createProduct(UUID.randomUUID().toString(), "D".repeat(255)));
        entityManager.clear();

        assertThatThrownBy(() -> productRepository.saveAndFlush(
                createProduct(UUID.randomUUID().toString(), "D".repeat(256))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("value too long for type character varying(255)");
    }

    @ParameterizedTest
    @ValueSource(longs = { 3, 10 })
    void shouldAtomicallyDecreaseStockAndReportOneUpdatedRow(long requestedQuantity) {
        UUID productId = productRepository.saveAndFlush(createProduct()).getId();

        int updatedRows = productRepository.decreaseStockIfAvailable(productId, requestedQuantity);
        entityManager.clear();

        assertThat(updatedRows).isEqualTo(1);
        assertThat(productRepository.findById(productId).orElseThrow().getQuantity())
                .isEqualTo(10L - requestedQuantity);
    }

    @ParameterizedTest
    @ValueSource(longs = { 11, 0, -1 })
    void shouldLeaveStockUnchangedWhenDecreaseCannotBeApplied(long requestedQuantity) {
        UUID productId = productRepository.saveAndFlush(createProduct()).getId();

        int updatedRows = productRepository.decreaseStockIfAvailable(productId, requestedQuantity);
        entityManager.clear();

        assertThat(updatedRows).isZero();
        assertThat(productRepository.findById(productId).orElseThrow().getQuantity()).isEqualTo(10L);
    }

    @Test
    void shouldReportNoUpdatedRowsForMissingProduct() {
        assertThat(productRepository.decreaseStockIfAvailable(UUID.randomUUID(), 1L)).isZero();
    }

    @Test
    void shouldAtomicallyIncreaseStockAndReportOneUpdatedRow() {
        UUID productId = productRepository.saveAndFlush(createProduct()).getId();

        int updatedRows = productRepository.increaseStockIfAvailable(productId, 3L);
        entityManager.clear();

        assertThat(updatedRows).isEqualTo(1);
        assertThat(productRepository.findById(productId).orElseThrow().getQuantity()).isEqualTo(13L);
    }

    @Test
    void shouldIncreaseOnlyTheRequestedProductStock() {
        UUID requestedProductId = productRepository.saveAndFlush(createProduct()).getId();
        UUID otherProductId = productRepository.saveAndFlush(createProduct()).getId();

        int updatedRows = productRepository.increaseStockIfAvailable(requestedProductId, 3L);
        entityManager.clear();

        assertThat(updatedRows).isEqualTo(1);
        assertThat(productRepository.findById(requestedProductId).orElseThrow().getQuantity()).isEqualTo(13L);
        assertThat(productRepository.findById(otherProductId).orElseThrow().getQuantity()).isEqualTo(10L);
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, -1 })
    void shouldLeaveStockUnchangedWhenIncreaseQuantityIsNotPositive(long requestedQuantity) {
        UUID productId = productRepository.saveAndFlush(createProduct()).getId();

        int updatedRows = productRepository.increaseStockIfAvailable(productId, requestedQuantity);
        entityManager.clear();

        assertThat(updatedRows).isZero();
        assertThat(productRepository.findById(productId).orElseThrow().getQuantity()).isEqualTo(10L);
    }

    @Test
    void shouldReportNoIncreasedRowsForMissingProduct() {
        assertThat(productRepository.increaseStockIfAvailable(UUID.randomUUID(), 3L)).isZero();
    }

    private ProductEntity createProduct() {
        return createProduct(UUID.randomUUID().toString(), "Test description");
    }

    private ProductEntity createProduct(String sku, String description) {
        return ProductEntity.builder()
                .sku(sku)
                .name("Test Product")
                .description(description)
                .price(new BigDecimal("123.50"))
                .currency(Currency.MXN)
                .quantity(10L)
                .active(true)
                .build();
    }
}
