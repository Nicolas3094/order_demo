package com.orders.messages.orders_demo.app.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.orders.messages.orders_demo.app.product.internal.ProductMapper;
import com.orders.messages.orders_demo.app.product.internal.exceptions.InvalidProductException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orders.messages.orders_demo.app.product.api.ProductResponse;
import com.orders.messages.orders_demo.app.product.internal.ProductEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductRepository;
import com.orders.messages.orders_demo.app.product.internal.exceptions.InsufficientStockException;
import com.orders.messages.orders_demo.app.product.internal.exceptions.ProductNotFoundException;

@ExtendWith(MockitoExtension.class)
public class ProductInternalApiImplTest {

    private static final String DEFAULT_SKU = "SKU1";

    @Mock
    private ProductRepository productRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private ProductInternalApiImpl productInternalApi;

    @Test
    public void getProductBySku_WhenProductExists_ShouldReturnProductResponse() {
        ProductEntity product = createProduct();
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.of(product));

        ProductResponse result = productInternalApi.getProductBySku(DEFAULT_SKU);

        assertEquals(DEFAULT_SKU, result.sku());
        verify(productRepository).findBySku(DEFAULT_SKU);
    }

    @Test
    public void getProductBySku_WhenProductDoesNotExist_ShouldThrowProductNotFoundException() {
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.empty());

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> productInternalApi.getProductBySku(DEFAULT_SKU));

        verify(productRepository).findBySku(DEFAULT_SKU);
        assertEquals("Product could not be found.", result.getMessage());
    }

    @Test
    public void increaceProductStock_WhenProductExists_ShouldIncreaseStockAndSaveProduct() {
        ProductEntity product = createProductWithStock(5L);
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.of(product));

        productInternalApi.increaseProductStock(DEFAULT_SKU, 3L);

        verify(productRepository).findBySku(DEFAULT_SKU);
        verify(productRepository).save(product);
        assertEquals(8L, product.getQuantity());
    }

    @Test
    public void increaceProductStock_WhenProductDoesNotExist_ShouldThrowProductNotFoundException() {
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class,
                () -> productInternalApi.increaseProductStock(DEFAULT_SKU, 3L));

        verify(productRepository).findBySku(DEFAULT_SKU);
        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    @Test
    void decreaseProductStock_WhenProductExists_ShouldUpdateStockWithoutSavingStaleEntity() {
        ProductEntity product = createProductWithStock(5L);
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.of(product));
        when(productRepository.decreaseStockIfAvailable(product.getId(), 3L)).thenReturn(1);

        productInternalApi.decreaseProductStock(DEFAULT_SKU, 3L);

        verify(productRepository).decreaseStockIfAvailable(product.getId(), 3L);
        verify(entityManager).refresh(product);
        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, -1 })
    void decreaseProductStock_WhenQuantityIsNotPositive_ShouldRejectWithoutUpdating(long quantity) {
        assertThrows(IllegalArgumentException.class,
                () -> productInternalApi.decreaseProductStock(DEFAULT_SKU, quantity));

        verifyNoInteractions(productRepository, entityManager);
    }

    @Test
    public void decreaceProductStock_WhenProductDoesNotExist_ShouldThrowProductNotFoundException() {
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class,
                () -> productInternalApi.decreaseProductStock(DEFAULT_SKU, 3L));

        verify(productRepository).findBySku(DEFAULT_SKU);
        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    @Test
    public void decreaceProductStock_WhenProductHasInsufficientStock_ShouldThrowInsufficientStockException() {
        ProductEntity product = createProductWithStock(2L);
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.of(product));

        InsufficientStockException result = assertThrows(InsufficientStockException.class,
                () -> productInternalApi.decreaseProductStock(DEFAULT_SKU, 3L));

        verify(productRepository).findBySku(DEFAULT_SKU);
        verify(productRepository, never()).save(any(ProductEntity.class));
        verifyNoInteractions(entityManager);
        assertEquals("Insufficient stock.", result.getMessage());
    }

    @ParameterizedTest
    @ValueSource(longs = { 1, 5 })
    void validateHasEnoughStock_WhenAvailable_ShouldAcceptWithoutSaving(long quantity) {
        var product = createProductWithStock(5L);

        productInternalApi.validateHasEnoughStock(ProductMapper.toResponse(product), quantity);

        assertEquals(5L, product.getQuantity());
        verifyNoInteractions(productRepository);
    }

    @Test
    void validateHasEnoughStock_WhenInsufficient_ShouldRejectWithoutSaving() {
        var product = ProductMapper.toResponse(createProductWithStock(5L));

        assertThrows(InvalidProductException.class, () -> productInternalApi.validateHasEnoughStock(product, 6L));

        verifyNoInteractions(productRepository);
    }

    @Test
    void validateIsActiveProduct_WhenInactive_ShouldReject() {
        var product = createProduct();
        product.deactivate();

        assertThrows(InvalidProductException.class, () -> productInternalApi.validateIsActiveProduct(ProductMapper.toResponse(product)));

        verifyNoInteractions(productRepository);
    }

    @Test
    void validateIsActiveProduct_WhenActive_ShouldAcceptWithoutSaving() {
        productInternalApi.validateIsActiveProduct(ProductMapper.toResponse(createProduct()));

        verifyNoInteractions(productRepository);
    }

    private ProductEntity createProduct() {
        return createProductWithStock(10L);
    }

    private ProductEntity createProductWithStock(long quantity) {
        return ProductEntity.builder()
                .price(new BigDecimal("123.00"))
                .name("Product name")
                .description("product description")
                .sku(DEFAULT_SKU)
                .quantity(quantity)
                .build();
    }
}