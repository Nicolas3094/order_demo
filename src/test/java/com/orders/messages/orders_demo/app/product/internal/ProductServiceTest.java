package com.orders.messages.orders_demo.app.product.internal;

import jakarta.persistence.EntityManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import com.orders.messages.orders_demo.app.product.internal.exceptions.InsufficientStockException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.product.api.CreateProductRequest;
import com.orders.messages.orders_demo.app.product.api.ProductResponse;
import com.orders.messages.orders_demo.app.product.internal.exceptions.InvalidProductException;
import com.orders.messages.orders_demo.app.product.internal.exceptions.ProductNotFoundException;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    private static final String PRODUCT_NOT_FOUND_MESSAGE = "Product could not be found.";
    private static final String DUPLICATED_SKU_MESSAGE = "Product must have unique SKU.";
    private static final String DEFAULT_SKU = "SKU-001";
    private static final String DEFAULT_NAME = "Product";
    private static final String DEFAULT_DESCRIPTION = "Description";
    private static final BigDecimal DEFAULT_PRICE = new BigDecimal("100.00");
    private static final Long DEFAULT_QUANTITY = 10L;

    private UUID productId;
    private ArgumentCaptor<ProductEntity> productCaptor;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private ProductService productService;

    @BeforeEach
    public void setup() {
        productId = UUID.randomUUID();
        productCaptor = ArgumentCaptor.forClass(ProductEntity.class);
    }

    @Test
    public void getProduct_WhenProductFound_ShouldReturnProduct() {
        ProductEntity product = createProduct(productId);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        ProductResponse result = productService.getProduct(productId);

        verify(productRepository).findById(productId);
        assertEquals(productId, result.id());
    }

    @Test
    public void getProduct_WhenProductNotFound_ShouldThrowProductNotFoundException() {

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> productService.getProduct(productId));

        assertEquals(PRODUCT_NOT_FOUND_MESSAGE, result.getMessage());
    }

    @Test
    public void getAllProducts_ShouldReturnProducts() {

        List<ProductEntity> products = List.of(createProduct(productId),
                createProduct(UUID.randomUUID()).toBuilder()
                        .sku("SKU-002")
                        .name("Other")
                        .build());
        when(productRepository.findAll()).thenReturn(products);

        List<ProductResponse> result = productService.getAllProducts();

        assertEquals(2, result.size());
        assertEquals(products.get(0).getId(), result.get(0).id());
        assertEquals(products.get(1).getId(), result.get(1).id());
        verify(productRepository).findAll();
    }

    @Test
    public void createProduct_WhenSkuIsUnique_ShouldCreateProduct() {
        CreateProductRequest request = createProductRequest();
        when(productRepository.existsBySku(DEFAULT_SKU)).thenReturn(false);
        when(productRepository.save(any(ProductEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse result = productService.createProduct(request);

        assertEquals(DEFAULT_SKU, result.sku());
        assertEquals(DEFAULT_NAME, result.name());
        assertEquals(DEFAULT_DESCRIPTION, result.description());
        assertEquals(DEFAULT_PRICE, result.price());
        assertEquals(Currency.MXN, result.currency());
        assertEquals(DEFAULT_QUANTITY, result.quantity());
        verify(productRepository).save(any(ProductEntity.class));
    }

    @Test
    public void createProduct_WhenSkuAlreadyExists_ShouldThrowInvalidProductException() {
        CreateProductRequest request = createProductRequest();
        when(productRepository.existsBySku(DEFAULT_SKU)).thenReturn(true);

        InvalidProductException result = assertThrows(InvalidProductException.class,
                () -> productService.createProduct(request));

        assertEquals(DUPLICATED_SKU_MESSAGE, result.getMessage());
        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    @Test
    public void deleteProduct_WhenProductFound_ShouldDeleteProduct() {
        ProductEntity product = createProduct(productId);
        when(productRepository.findById(productId))
                .thenReturn(Optional.of(product));

        productService.deleteProduct(productId);
        verify(productRepository).delete(product);
    }

    @Test
    public void deleteProduct_WhenProductNotFound_ShouldThrowProductNotFoundException() {

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> productService.deleteProduct(productId));

        assertEquals(PRODUCT_NOT_FOUND_MESSAGE, result.getMessage());
        verify(productRepository, never()).delete(any(ProductEntity.class));
    }

    @Test
    public void changePrice_WhenProductFound_ShouldChangePrice() {
        BigDecimal newPrice = new BigDecimal("250.00");
        ProductEntity product = createProduct(productId);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(ProductEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse result = productService.changePrice(productId, newPrice);

        verify(productRepository).save(productCaptor.capture());
        assertEquals(result.id(), productCaptor.getValue().getId());
        assertEquals(newPrice, result.price());
    }

    @Test
    public void changePrice_WhenProductNotFound_ShouldThrowProductNotFoundException() {

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> productService.changePrice(productId, DEFAULT_PRICE));

        assertEquals(PRODUCT_NOT_FOUND_MESSAGE, result.getMessage());
        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    @Test
    void increaseStock_WhenProductFound_ShouldReturnRefreshedStockWithoutSaving() {
        ProductEntity product = createProduct(productId);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.increaseStockIfAvailable(productId, 20L)).thenReturn(1);
        doAnswer(invocation -> {
            ReflectionTestUtils.setField(product, "quantity", 30L);
            return null;
        }).when(entityManager).refresh(product);

        ProductResponse productResponse = productService.increaseStock(productId, 20L);

        assertEquals(productId, productResponse.id());
        assertEquals(30L, productResponse.quantity());
        verify(productRepository).increaseStockIfAvailable(productId, 20L);
        verify(entityManager).refresh(product);
        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    @Test
    void increaseStock_WhenUpdateAffectsNoRows_ShouldRejectWithoutRefreshing() {
        ProductEntity product = createProduct(productId);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.increaseStockIfAvailable(productId, 2L)).thenReturn(0);

        ProductNotFoundException productNotFoundException = assertThrows(ProductNotFoundException.class,
                () -> productService.increaseStock(productId, 2L));

        assertEquals(PRODUCT_NOT_FOUND_MESSAGE, productNotFoundException.getMessage());
        verifyNoInteractions(entityManager);
        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, -1 })
    void increaseStock_WhenQuantityIsNotPositive_ShouldRejectWithoutUpdating(long quantity) {
        IllegalArgumentException invalidQuantityException = assertThrows(IllegalArgumentException.class,
                () -> productService.increaseStock(productId, quantity));

        assertEquals("Quantity must be positive.", invalidQuantityException.getMessage());
        verifyNoInteractions(productRepository, entityManager);
    }

    @Test
    void increaseStock_WhenQuantityIsNull_ShouldRejectWithoutUpdating() {
        assertThrows(IllegalArgumentException.class, () -> productService.increaseStock(productId, null));

        verifyNoInteractions(productRepository, entityManager);
    }

    @Test
    public void increaseStock_WhenProductNotFound_ShouldThrowProductNotFoundException() {

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> productService.increaseStock(productId, 20L));

        assertEquals(PRODUCT_NOT_FOUND_MESSAGE, result.getMessage());
        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    @Test
    void decreaseStock_WhenProductFound_ShouldReturnRefreshedStockWithoutSaving() {
        ProductEntity product = createProduct(productId);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.decreaseStockIfAvailable(productId, 5L)).thenReturn(1);
        doAnswer(invocation -> {
            ReflectionTestUtils.setField(product, "quantity", 5L);
            return null;
        }).when(entityManager).refresh(product);

        ProductResponse productResponse = productService.decreaseStock(productId, 5L);

        assertEquals(productId, productResponse.id());
        assertEquals(5L, productResponse.quantity());
        verify(productRepository).decreaseStockIfAvailable(productId, 5L);
        verify(entityManager).refresh(product);
        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    @Test
    void decreaseStock_WhenUpdateAffectsNoRows_ShouldRejectWithoutSaving() {
        ProductEntity product = createProduct(productId);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.decreaseStockIfAvailable(productId, 11L)).thenReturn(0);

        assertThrows(InsufficientStockException.class, () -> productService.decreaseStock(productId, 11L));

        verifyNoInteractions(entityManager);
        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, -1 })
    void decreaseStock_WhenQuantityIsNotPositive_ShouldRejectWithoutUpdating(long quantity) {
        assertThrows(IllegalArgumentException.class, () -> productService.decreaseStock(productId, quantity));

        verifyNoInteractions(productRepository, entityManager);
    }

    @Test
    public void decreaseStock_WhenProductNotFound_ShouldThrowProductNotFoundException() {

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> productService.decreaseStock(productId, 5L));

        assertEquals(PRODUCT_NOT_FOUND_MESSAGE, result.getMessage());
        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    @Test
    public void changeCurrency_WhenProductFound_ShouldChangeCurrency() {
        ProductEntity product = createProduct(productId);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(ProductEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse result = productService.changeCurrency(productId, Currency.USD);

        verify(productRepository).save(productCaptor.capture());
        assertEquals(result.id(), productCaptor.getValue().getId());
        assertEquals(Currency.USD, result.currency());
    }

    @Test
    public void changeCurrency_WhenProductNotFound_ShouldThrowProductNotFoundException() {

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> productService.changeCurrency(productId, Currency.USD));

        assertEquals(PRODUCT_NOT_FOUND_MESSAGE, result.getMessage());
        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    @Test
    public void changeName_WhenProductFound_ShouldChangeName() {
        ProductEntity product = createProduct(productId);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(ProductEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse result = productService.changeName(productId, "New Product");

        verify(productRepository).save(productCaptor.capture());
        assertEquals(result.id(), productCaptor.getValue().getId());
        assertEquals("New Product", result.name());
    }

    @Test
    public void changeName_WhenProductNotFound_ShouldThrowProductNotFoundException() {

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> productService.changeName(productId, "New Product"));

        assertEquals(PRODUCT_NOT_FOUND_MESSAGE, result.getMessage());
        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    @Test
    public void changeDescription_WhenProductFound_ShouldChangeDescription() {
        ProductEntity product = createProduct(productId);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(ProductEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse result = productService.changeDescription(productId, "New Description");

        verify(productRepository).save(productCaptor.capture());
        assertEquals(result.id(), productCaptor.getValue().getId());
        assertEquals("New Description", result.description());
    }

    @Test
    public void changeDescription_WhenProductNotFound_ShouldThrowProductNotFoundException() {

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> productService.changeDescription(productId, "New Description"));

        assertEquals(PRODUCT_NOT_FOUND_MESSAGE, result.getMessage());
        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    @Test
    public void activate_WhenProductFound_ShouldActivateProduct() {
        ProductEntity product = createProduct(productId);
        product.deactivate();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(ProductEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse result = productService.activate(productId);

        verify(productRepository).save(productCaptor.capture());
        assertEquals(result.id(), productCaptor.getValue().getId());
        assertTrue(result.active());
    }

    @Test
    public void activate_WhenProductNotFound_ShouldThrowProductNotFoundException() {

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> productService.activate(productId));

        assertEquals(PRODUCT_NOT_FOUND_MESSAGE, result.getMessage());
        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    @Test
    public void deactivate_WhenProductFound_ShouldDeactivateProduct() {
        ProductEntity product = createProduct(productId);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(ProductEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse result = productService.deactivate(productId);

        verify(productRepository).save(productCaptor.capture());
        assertEquals(result.id(), productCaptor.getValue().getId());
        assertFalse(result.active());
    }

    @Test
    public void deactivate_WhenProductNotFound_ShouldThrowProductNotFoundException() {

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> productService.deactivate(productId));

        assertEquals(PRODUCT_NOT_FOUND_MESSAGE, result.getMessage());
        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    private static ProductEntity createProduct(UUID id) {
        return ProductEntity.builder()
                .id(id)
                .sku(DEFAULT_SKU)
                .name(DEFAULT_NAME)
                .description(DEFAULT_DESCRIPTION)
                .price(DEFAULT_PRICE)
                .quantity(DEFAULT_QUANTITY)
                .build();
    }

    private static CreateProductRequest createProductRequest() {
        return CreateProductRequest.builder()
                .sku(DEFAULT_SKU)
                .name(DEFAULT_NAME)
                .description(DEFAULT_DESCRIPTION)
                .price(DEFAULT_PRICE)
                .quantity(DEFAULT_QUANTITY)
                .currency(Currency.MXN)
                .build();
    }
}
