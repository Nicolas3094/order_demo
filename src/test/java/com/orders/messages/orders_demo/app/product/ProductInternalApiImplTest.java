package com.orders.messages.orders_demo.app.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
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

        productInternalApi.increaceProductStock(DEFAULT_SKU, 3L);

        verify(productRepository).findBySku(DEFAULT_SKU);
        verify(productRepository).save(product);
        assertEquals(8L, product.getQuantity());
    }

    @Test
    public void increaceProductStock_WhenProductDoesNotExist_ShouldThrowProductNotFoundException() {
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class,
                () -> productInternalApi.increaceProductStock(DEFAULT_SKU, 3L));

        verify(productRepository).findBySku(DEFAULT_SKU);
        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    @Test
    public void decreaceProductStock_WhenProductExists_ShouldDecreaseStockAndSaveProduct() {
        ProductEntity product = createProductWithStock(5L);
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.of(product));

        productInternalApi.decreaceProductStock(DEFAULT_SKU, 3L);

        verify(productRepository).findBySku(DEFAULT_SKU);
        verify(productRepository).save(product);
        assertEquals(2L, product.getQuantity());
    }

    @Test
    public void decreaceProductStock_WhenProductDoesNotExist_ShouldThrowProductNotFoundException() {
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class,
                () -> productInternalApi.decreaceProductStock(DEFAULT_SKU, 3L));

        verify(productRepository).findBySku(DEFAULT_SKU);
        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    @Test
    public void decreaceProductStock_WhenProductHasInsufficientStock_ShouldThrowInsufficientStockException() {
        ProductEntity product = createProductWithStock(2L);
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.of(product));

        InsufficientStockException result = assertThrows(InsufficientStockException.class,
                () -> productInternalApi.decreaceProductStock(DEFAULT_SKU, 3L));

        verify(productRepository).findBySku(DEFAULT_SKU);
        verify(productRepository, never()).save(any(ProductEntity.class));
        assertEquals("Insufficient stock.", result.getMessage());
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