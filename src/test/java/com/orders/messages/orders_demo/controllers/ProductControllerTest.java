package com.orders.messages.orders_demo.controllers;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.messages.orders_demo.dtos.request.ChangeCurrencyRequest;
import com.orders.messages.orders_demo.dtos.request.ChangeDescriptionRequest;
import com.orders.messages.orders_demo.dtos.request.ChangeNameRequest;
import com.orders.messages.orders_demo.dtos.request.ChangePriceRequest;
import com.orders.messages.orders_demo.dtos.request.CreateProductRequest;
import com.orders.messages.orders_demo.dtos.request.DecreaseStockRequest;
import com.orders.messages.orders_demo.dtos.request.IncreaseStockRequest;
import com.orders.messages.orders_demo.entity.Product;
import com.orders.messages.orders_demo.enums.Currency;
import com.orders.messages.orders_demo.exceptions.product.InvalidProductException;
import com.orders.messages.orders_demo.exceptions.product.ProductNotFoundException;
import com.orders.messages.orders_demo.services.ProductService;

@WebMvcTest(ProductController.class)
public class ProductControllerTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    private static final String DEFAULT_SKU = "ABC123";
    private static final String DEFAULT_NAME = "Test Product";
    private static final String DEFAULT_DESCRIPTION = "This is a test product.";
    private static final BigDecimal DEFAULT_PRICE = new BigDecimal("123.0");
    private static final long DEFAULT_QUANTITY = 10L;

    private UUID productId;

    @BeforeEach
    public void setUp() {
        productId = UUID.randomUUID();
    }

    @Test
    public void getProduct_WhenProductExists_ShouldReturn200() throws Exception {
        Product product = createProduct();
        when(productService.getProduct(productId)).thenReturn(product);

        mvc.perform(get("/api/v1/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value(DEFAULT_SKU))
                .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
                .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
                .andExpect(jsonPath("$.price").value(DEFAULT_PRICE))
                .andExpect(jsonPath("$.currency").value(Currency.MXN.name()))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.quantity").value(DEFAULT_QUANTITY));
        verify(productService).getProduct(productId);
    }

    @Test
    public void getProduct_WhenProductNotFound_ShouldReturn404() throws Exception {
        when(productService.getProduct(productId)).thenThrow(new ProductNotFoundException());

        mvc.perform(get("/api/v1/products/{productId}", productId))
                .andExpect(status().isNotFound());
        verify(productService).getProduct(productId);
    }

    @Test
    public void getAllProducts_ShouldReturn200() throws Exception {
        when(productService.getAllProducts()).thenReturn(List.of(createProduct()));

        mvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value(DEFAULT_SKU))
                .andExpect(jsonPath("$[0].currency").value(Currency.MXN.name()));
        verify(productService).getAllProducts();
    }

    @Test
    public void createProduct_WhenRequestIsValid_ShouldReturn201() throws Exception {
        CreateProductRequest request = createProductRequest();
        Product product = createProduct();
        when(productService.createProduct(any(CreateProductRequest.class))).thenReturn(product);

        mvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value(DEFAULT_SKU))
                .andExpect(jsonPath("$.currency").value(Currency.MXN.name()));
        verify(productService).createProduct(any(CreateProductRequest.class));
    }

    @Test
    public void createProduct_WhenInvalidProductException_ShouldReturn409() throws Exception {
        CreateProductRequest request = createProductRequest();
        when(productService.createProduct(any(CreateProductRequest.class)))
                .thenThrow(new InvalidProductException("Invalid product request."));

        mvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Invalid product request."))
                .andExpect(jsonPath("$.path").value("/api/v1/products"));
        verify(productService).createProduct(any(CreateProductRequest.class));
    }

    @Test
    public void createProduct_WhenSkuIsBlank_ShouldReturn400() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .sku("")
                .name(DEFAULT_NAME)
                .description(DEFAULT_DESCRIPTION)
                .price(DEFAULT_PRICE)
                .currency(Currency.MXN)
                .quantity(DEFAULT_QUANTITY)
                .build();
        Product product = createProduct();
        when(productService.createProduct(any(CreateProductRequest.class))).thenReturn(product);

        mvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("SKU must not be blank."))
                .andExpect(jsonPath("$.path").value("/api/v1/products"));
        verify(productService, never()).createProduct(any(CreateProductRequest.class));
    }

    @Test
    public void createProduct_WhenNameIsBlank_ShouldReturn400() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .sku(DEFAULT_SKU)
                .name("")
                .description(DEFAULT_DESCRIPTION)
                .price(DEFAULT_PRICE)
                .currency(Currency.MXN)
                .quantity(DEFAULT_QUANTITY)
                .build();
        Product product = createProduct();
        when(productService.createProduct(any(CreateProductRequest.class))).thenReturn(product);

        mvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Name must not be blank."))
                .andExpect(jsonPath("$.path").value("/api/v1/products"));
        verify(productService, never()).createProduct(any(CreateProductRequest.class));
    }

    @Test
    public void createProduct_WhenDescriptionIsBlank_ShouldReturn400() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .sku(DEFAULT_SKU)
                .name(DEFAULT_NAME)
                .description("")
                .price(DEFAULT_PRICE)
                .currency(Currency.MXN)
                .quantity(DEFAULT_QUANTITY)
                .build();
        Product product = createProduct();
        when(productService.createProduct(any(CreateProductRequest.class))).thenReturn(product);

        mvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Description must not be blank."))
                .andExpect(jsonPath("$.path").value("/api/v1/products"));
        verify(productService, never()).createProduct(any(CreateProductRequest.class));
    }

    @Test
    public void createProduct_WhenPriceIsNegative_ShouldReturn400() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .sku(DEFAULT_SKU)
                .name(DEFAULT_NAME)
                .description(DEFAULT_DESCRIPTION)
                .price(new BigDecimal("-10.00"))
                .currency(Currency.MXN)
                .quantity(DEFAULT_QUANTITY)
                .build();
        Product product = createProduct();
        when(productService.createProduct(any(CreateProductRequest.class))).thenReturn(product);

        mvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Price must be positive."))
                .andExpect(jsonPath("$.path").value("/api/v1/products"));
        verify(productService, never()).createProduct(any(CreateProductRequest.class));
    }

    @Test
    public void createProduct_WhenCurrencyIsNull_ShouldReturn400() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .sku(DEFAULT_SKU)
                .name(DEFAULT_NAME)
                .description(DEFAULT_DESCRIPTION)
                .price(DEFAULT_PRICE)
                .currency(null)
                .quantity(DEFAULT_QUANTITY)
                .build();
        Product product = createProduct();
        when(productService.createProduct(any(CreateProductRequest.class))).thenReturn(product);

        mvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Currency is required."))
                .andExpect(jsonPath("$.path").value("/api/v1/products"));
        verify(productService, never()).createProduct(any(CreateProductRequest.class));
    }

    @Test
    public void createProduct_WhenQuantityIsNegative_ShouldReturn400() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .sku(DEFAULT_SKU)
                .name(DEFAULT_NAME)
                .description(DEFAULT_DESCRIPTION)
                .price(DEFAULT_PRICE)
                .currency(Currency.MXN)
                .quantity(-1L)
                .build();
        Product product = createProduct();
        when(productService.createProduct(any(CreateProductRequest.class))).thenReturn(product);

        mvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Quantity must be zero or positive."))
                .andExpect(jsonPath("$.path").value("/api/v1/products"));
        verify(productService, never()).createProduct(any(CreateProductRequest.class));
    }

    @Test
    public void changePrice_WhenRequestIsValid_ShouldReturn200() throws Exception {
        Product updated = createProduct();
        updated.changePrice(new BigDecimal("250.00"));
        when(productService.changePrice(eq(productId), any(BigDecimal.class)))
                .thenReturn(updated);

        mvc.perform(patch("/api/v1/products/{productId}/price", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ChangePriceRequest(new BigDecimal("250.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(250.00));
        verify(productService).changePrice(eq(productId), any(BigDecimal.class));
    }

    @ParameterizedTest
    @ValueSource(strings = { "-1.00", "0.00" })
    public void changePrice_WhenChangePriceIsNegativeOrZero_ShouldReturn400(String price) throws Exception {
        Product updated = createProduct();
        when(productService.changePrice(eq(productId), any(BigDecimal.class)))
                .thenReturn(updated);

        mvc.perform(patch("/api/v1/products/{productId}/price", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ChangePriceRequest(new BigDecimal(price)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Price must be a positive value."))
                .andExpect(jsonPath("$.path").value("/api/v1/products/" + productId + "/price"));
        verify(productService, never()).changePrice(eq(productId), any(BigDecimal.class));
    }

    @Test
    public void changePrice_WhenProductNotFound_ShouldReturn404() throws Exception {
        when(productService.changePrice(eq(productId), any(BigDecimal.class)))
                .thenThrow(new ProductNotFoundException());

        mvc.perform(patch("/api/v1/products/{productId}/price", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ChangePriceRequest(new BigDecimal("250.00")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Product could not be found."))
                .andExpect(jsonPath("$.path").value("/api/v1/products/" + productId + "/price"));
        verify(productService).changePrice(eq(productId), any(BigDecimal.class));
    }

    @Test
    public void increaseStock_WhenRequestIsValid_ShouldReturn200() throws Exception {
        Product updated = createProduct();
        updated.increaseStock(20);
        when(productService.increaseStock(productId, 20L)).thenReturn(updated);

        mvc.perform(patch("/api/v1/products/{productId}/increase-stock", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new IncreaseStockRequest(20L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(DEFAULT_QUANTITY + 20));
        verify(productService).increaseStock(productId, 20L);
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, 0 })
    public void increaseStock_WhenStockChangeIsNegativeOrZero_ShouldReturn400(int stock) throws Exception {
        Product updated = createProduct();
        when(productService.increaseStock(eq(productId), any(Long.class)))
                .thenReturn(updated);

        mvc.perform(patch("/api/v1/products/{productId}/increase-stock", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new IncreaseStockRequest((long) stock))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Stock change must be a positive value."))
                .andExpect(jsonPath("$.path").value("/api/v1/products/" + productId + "/increase-stock"));
        verify(productService, never()).increaseStock(eq(productId), any(Long.class));
    }

    @Test
    public void increaseStock_WhenProductNotFound_ShouldReturn404() throws Exception {
        when(productService.increaseStock(eq(productId), any(Long.class)))
                .thenThrow(new ProductNotFoundException());

        mvc.perform(patch("/api/v1/products/{productId}/increase-stock", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new IncreaseStockRequest(20L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Product could not be found."))
                .andExpect(jsonPath("$.path").value("/api/v1/products/" + productId + "/increase-stock"));
        verify(productService).increaseStock(eq(productId), any(Long.class));
    }

    @Test
    public void decreaseStock_WhenRequestIsValid_ShouldReturn200() throws Exception {
        Product updated = createProduct();
        updated.decreaseStock(5);
        when(productService.decreaseStock(productId, 5L)).thenReturn(updated);

        mvc.perform(patch("/api/v1/products/{productId}/decrease-stock", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new DecreaseStockRequest(5L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(DEFAULT_QUANTITY - 5));
        verify(productService).decreaseStock(productId, 5L);
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, 0 })
    public void decreaseStock_WhenStockChangeIsNegativeOrZero_ShouldReturn400(int stock) throws Exception {
        Product updated = createProduct();
        when(productService.decreaseStock(eq(productId), any(Long.class)))
                .thenReturn(updated);

        mvc.perform(patch("/api/v1/products/{productId}/decrease-stock", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new DecreaseStockRequest((long) stock))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Stock change must be a positive value."))
                .andExpect(jsonPath("$.path").value("/api/v1/products/" + productId + "/decrease-stock"));
        verify(productService, never()).decreaseStock(eq(productId), any(Long.class));
    }

    @Test
    public void decreaseStock_WhenProductNotFound_ShouldReturn404() throws Exception {
        when(productService.decreaseStock(eq(productId), any(Long.class)))
                .thenThrow(new ProductNotFoundException());

        mvc.perform(patch("/api/v1/products/{productId}/decrease-stock", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new DecreaseStockRequest(5L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Product could not be found."))
                .andExpect(jsonPath("$.path").value("/api/v1/products/" + productId + "/decrease-stock"));
        verify(productService).decreaseStock(eq(productId), any(Long.class));
    }

    @Test
    public void changeCurrency_WhenRequestIsValid_ShouldReturn200() throws Exception {
        Product updated = createProduct();
        updated.changeCurrency(Currency.USD);
        when(productService.changeCurrency(productId, Currency.USD))
                .thenReturn(updated);

        mvc.perform(patch("/api/v1/products/{productId}/currency", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ChangeCurrencyRequest(Currency.USD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"));
        verify(productService).changeCurrency(productId, Currency.USD);
    }

    @Test
    public void changeCurrency_WhenCurrencyIsInvalid_ShouldReturn400() throws Exception {
        Product updated = createProduct();
        when(productService.changeCurrency(eq(productId), any(Currency.class))).thenReturn(updated);

        mvc.perform(patch("/api/v1/products/{productId}/currency", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ChangeCurrencyRequest(null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Currency is required."));
        verify(productService, never()).changeCurrency(eq(productId), any(Currency.class));
    }

    @Test
    public void changeCurrency_WhenProductNotFound_ShouldReturn404() throws Exception {
        when(productService.changeCurrency(eq(productId), any(Currency.class)))
                .thenThrow(new ProductNotFoundException());

        mvc.perform(patch("/api/v1/products/{productId}/currency", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ChangeCurrencyRequest(Currency.USD))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Product could not be found."))
                .andExpect(jsonPath("$.path").value("/api/v1/products/" + productId + "/currency"));
        verify(productService).changeCurrency(eq(productId), any(Currency.class));
    }

    @Test
    public void changeName_WhenRequestIsValid_ShouldReturn200() throws Exception {
        Product updated = createProduct();
        updated.changeName("New Product Name");
        when(productService.changeName(productId, "New Product Name"))
                .thenReturn(updated);

        mvc.perform(patch("/api/v1/products/{productId}/name", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangeNameRequest("New Product Name"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Product Name"));
        verify(productService).changeName(productId, "New Product Name");
    }

    @Test
    public void changeName_WhenNameIsInvalid_ShouldReturn400() throws Exception {
        Product updated = createProduct();
        when(productService.changeName(eq(productId), any(String.class))).thenReturn(updated);

        mvc.perform(patch("/api/v1/products/{productId}/name", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ChangeNameRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Name cannot be blank"));
        verify(productService, never()).changeName(eq(productId), any(String.class));
    }

    @Test
    public void changeName_WhenProductNotFound_ShouldReturn404() throws Exception {
        when(productService.changeName(eq(productId), any(String.class)))
                .thenThrow(new ProductNotFoundException());

        mvc.perform(patch("/api/v1/products/{productId}/name", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ChangeNameRequest("New Product Name"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Product could not be found."))
                .andExpect(jsonPath("$.path").value("/api/v1/products/" + productId + "/name"));
        verify(productService).changeName(eq(productId), any(String.class));
    }

    @Test
    public void changeDescription_WhenRequestIsValid_ShouldReturn200() throws Exception {
        Product updated = createProduct();
        updated.changeDescription("New Description");
        when(productService.changeDescription(productId, "New Description"))
                .thenReturn(updated);

        mvc.perform(patch("/api/v1/products/{productId}/description", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangeDescriptionRequest("New Description"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("New Description"));
        verify(productService).changeDescription(productId, "New Description");
    }

    @Test
    public void changeDescription_WhenDescriptionIsInvalid_ShouldReturn400() throws Exception {
        Product updated = createProduct();
        when(productService.changeDescription(eq(productId), any(String.class))).thenReturn(updated);

        mvc.perform(patch("/api/v1/products/{productId}/description", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ChangeDescriptionRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Description cannot be blank"));
        verify(productService, never()).changeDescription(eq(productId), any(String.class));
    }

    @Test
    public void changeDescription_WhenProductNotFound_ShouldReturn404() throws Exception {
        when(productService.changeDescription(eq(productId), any(String.class)))
                .thenThrow(new ProductNotFoundException());

        mvc.perform(patch("/api/v1/products/{productId}/description", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ChangeDescriptionRequest("New Description"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Product could not be found."))
                .andExpect(jsonPath("$.path").value("/api/v1/products/" + productId + "/description"));
        verify(productService).changeDescription(eq(productId), any(String.class));
    }

    @Test
    public void activate_WhenRequestIsValid_ShouldReturn200() throws Exception {
        Product updated = createProduct();
        updated.activate();
        when(productService.activate(productId)).thenReturn(updated);

        mvc.perform(patch("/api/v1/products/{productId}/activate", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        verify(productService).activate(productId);
    }

    @Test
    public void activate_WhenProductNotFound_ShouldReturn404() throws Exception {
        when(productService.activate(eq(productId))).thenThrow(new ProductNotFoundException());

        mvc.perform(patch("/api/v1/products/{productId}/activate", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Product could not be found."))
                .andExpect(jsonPath("$.path").value("/api/v1/products/" + productId + "/activate"));
        verify(productService).activate(eq(productId));
    }

    @Test
    public void deactivate_WhenRequestIsValid_ShouldReturn200() throws Exception {
        Product updated = createProduct();
        updated.deactivate();
        when(productService.deactivate(productId)).thenReturn(updated);

        mvc.perform(patch("/api/v1/products/{productId}/deactivate", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        verify(productService).deactivate(productId);
    }

    @Test
    public void deactivate_WhenProductNotFound_ShouldReturn404() throws Exception {
        when(productService.deactivate(eq(productId))).thenThrow(new ProductNotFoundException());

        mvc.perform(patch("/api/v1/products/{productId}/deactivate", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Product could not be found."))
                .andExpect(jsonPath("$.path").value("/api/v1/products/" + productId + "/deactivate"));
        verify(productService).deactivate(eq(productId));
    }

    @Test
    public void deleteProduct_WhenProductExists_ShouldReturn204() throws Exception {
        doNothing().when(productService).deleteProduct(productId);

        mvc.perform(delete("/api/v1/products/{productId}", productId))
                .andExpect(status().isNoContent());
        verify(productService).deleteProduct(productId);
    }

    @Test
    public void deleteProduct_WhenProductNotFound_ShouldReturn404() throws Exception {
        doThrow(new ProductNotFoundException())
                .when(productService)
                .deleteProduct(productId);

        mvc.perform(delete("/api/v1/products/{productId}", productId))
                .andExpect(status().isNotFound());
    }

    private static Product createProduct() {
        return Product.builder()
                .sku(DEFAULT_SKU)
                .name(DEFAULT_NAME)
                .description(DEFAULT_DESCRIPTION)
                .price(DEFAULT_PRICE)
                .currency(Currency.MXN)
                .active(true)
                .quantity(DEFAULT_QUANTITY)
                .build();
    }

    private static CreateProductRequest createProductRequest() {
        return CreateProductRequest.builder()
                .sku(DEFAULT_SKU)
                .name(DEFAULT_NAME)
                .description(DEFAULT_DESCRIPTION)
                .price(DEFAULT_PRICE)
                .currency(Currency.MXN)
                .quantity(DEFAULT_QUANTITY)
                .build();
    }

}
