package com.orders.messages.orders_demo.services;

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
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orders.messages.orders_demo.dtos.request.CreateProductRequest;
import com.orders.messages.orders_demo.entity.Product;
import com.orders.messages.orders_demo.enums.Currency;
import com.orders.messages.orders_demo.exceptions.product.InvalidProductException;
import com.orders.messages.orders_demo.exceptions.product.ProductNotFoundException;
import com.orders.messages.orders_demo.repositories.ProductRepository;

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

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @BeforeEach
    public void setup() {
        productId = UUID.randomUUID();
    }

    @Test
    public void getProduct_WhenProductFound_ShouldReturnProduct() {
        Product product = createProduct();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        Product result = productService.getProduct(productId);

        assertEquals(product, result);
        verify(productRepository).findById(productId);
    }

    @Test
    public void getProduct_WhenProductNotFound_ShouldThrowProductNotFoundException() {

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> productService.getProduct(productId));

        assertEquals(PRODUCT_NOT_FOUND_MESSAGE, result.getMessage());
    }

    @Test
    public void getAllProducts_ShouldReturnProducts() {

        List<Product> products = List.of(createProduct(),
                createProduct().toBuilder()
                        .sku("SKU-002")
                        .name("Other")
                        .build());
        when(productRepository.findAll()).thenReturn(products);

        List<Product> result = productService.getAllProducts();

        assertEquals(products, result);
        verify(productRepository).findAll();
    }

    @Test
    public void createProduct_WhenSkuIsUnique_ShouldCreateProduct() {
        CreateProductRequest request = createProductRequest();
        when(productRepository.existsBySku(DEFAULT_SKU)).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product result = productService.createProduct(request);

        assertEquals(DEFAULT_SKU, result.getSku());
        assertEquals(DEFAULT_NAME, result.getName());
        assertEquals(DEFAULT_DESCRIPTION, result.getDescription());
        assertEquals(DEFAULT_PRICE, result.getPrice());
        assertEquals(Currency.MXN, result.getCurrency());
        assertEquals(DEFAULT_QUANTITY, result.getQuantity());
        verify(productRepository).save(any(Product.class));
    }

    @Test
    public void createProduct_WhenSkuAlreadyExists_ShouldThrowInvalidProductException() {
        CreateProductRequest request = createProductRequest();
        when(productRepository.existsBySku(DEFAULT_SKU)).thenReturn(true);

        InvalidProductException result = assertThrows(InvalidProductException.class,
                () -> productService.createProduct(request));

        assertEquals(DUPLICATED_SKU_MESSAGE, result.getMessage());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    public void deleteProduct_WhenProductFound_ShouldDeleteProduct() {
        Product product = createProduct();
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
        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    public void changePrice_WhenProductFound_ShouldChangePrice() {
        BigDecimal newPrice = new BigDecimal("250.00");
        Product product = createProduct();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product result = productService.changePrice(productId, newPrice);

        assertEquals(newPrice, result.getPrice());
        verify(productRepository).save(result);
    }

    @Test
    public void changePrice_WhenProductNotFound_ShouldThrowProductNotFoundException() {

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> productService.changePrice(productId, DEFAULT_PRICE));

        assertEquals(PRODUCT_NOT_FOUND_MESSAGE, result.getMessage());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    public void increaseStock_WhenProductFound_ShouldIncreaseStock() {
        Product product = createProduct();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product result = productService.increaseStock(productId, 20L);

        assertEquals(DEFAULT_QUANTITY + 20L, result.getQuantity());
        verify(productRepository).save(result);
    }

    @Test
    public void increaseStock_WhenProductNotFound_ShouldThrowProductNotFoundException() {

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> productService.increaseStock(productId, 20L));

        assertEquals(PRODUCT_NOT_FOUND_MESSAGE, result.getMessage());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    public void decreaseStock_WhenProductFound_ShouldDecreaseStock() {
        Product product = createProduct();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product result = productService.decreaseStock(productId, 5L);

        assertEquals(DEFAULT_QUANTITY - 5L, result.getQuantity());
        verify(productRepository).save(result);
    }

    @Test
    public void decreaseStock_WhenProductNotFound_ShouldThrowProductNotFoundException() {

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> productService.decreaseStock(productId, 5L));

        assertEquals(PRODUCT_NOT_FOUND_MESSAGE, result.getMessage());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    public void changeCurrency_WhenProductFound_ShouldChangeCurrency() {
        Product product = createProduct();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product result = productService.changeCurrency(productId, Currency.USD);

        assertEquals(Currency.USD, result.getCurrency());
        verify(productRepository).save(result);
    }

    @Test
    public void changeCurrency_WhenProductNotFound_ShouldThrowProductNotFoundException() {

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> productService.changeCurrency(productId, Currency.USD));

        assertEquals(PRODUCT_NOT_FOUND_MESSAGE, result.getMessage());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    public void changeName_WhenProductFound_ShouldChangeName() {
        Product product = createProduct();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product result = productService.changeName(productId, "New Product");

        assertEquals("New Product", result.getName());
        verify(productRepository).save(result);
    }

    @Test
    public void changeName_WhenProductNotFound_ShouldThrowProductNotFoundException() {

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> productService.changeName(productId, "New Product"));

        assertEquals(PRODUCT_NOT_FOUND_MESSAGE, result.getMessage());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    public void changeDescription_WhenProductFound_ShouldChangeDescription() {
        Product product = createProduct();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product result = productService.changeDescription(productId, "New Description");

        assertEquals("New Description", result.getDescription());
        verify(productRepository).save(result);
    }

    @Test
    public void changeDescription_WhenProductNotFound_ShouldThrowProductNotFoundException() {

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> productService.changeDescription(productId, "New Description"));

        assertEquals(PRODUCT_NOT_FOUND_MESSAGE, result.getMessage());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    public void activate_WhenProductFound_ShouldActivateProduct() {
        Product product = createProduct();
        product.deactivate();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product result = productService.activate(productId);

        assertTrue(result.getActive());
        verify(productRepository).save(result);
    }

    @Test
    public void activate_WhenProductNotFound_ShouldThrowProductNotFoundException() {

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> productService.activate(productId));

        assertEquals(PRODUCT_NOT_FOUND_MESSAGE, result.getMessage());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    public void deactivate_WhenProductFound_ShouldDeactivateProduct() {
        Product product = createProduct();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product result = productService.deactivate(productId);

        assertFalse(result.getActive());
        verify(productRepository).save(result);
    }

    @Test
    public void deactivate_WhenProductNotFound_ShouldThrowProductNotFoundException() {

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> productService.deactivate(productId));

        assertEquals(PRODUCT_NOT_FOUND_MESSAGE, result.getMessage());
        verify(productRepository, never()).save(any(Product.class));
    }

    private static Product createProduct() {
        return Product.builder()
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
