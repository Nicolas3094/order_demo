package com.orders.messages.orders_demo.app.product.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.product.api.*;

@AutoConfigureMockMvc
class ProductControllerIntegrationTest extends PostgresContainer {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<UUID> productIds = new ArrayList<>();
    private static final String BASE_PATH = "/api/v1/products";

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            productIds.forEach(productRepository::deleteById);
            productRepository.flush();
        });
        productIds.clear();
    }

    @Test
    void shouldCreateProductThroughHttpAndReturn201WithPersistedData() throws Exception {
        String sku = UUID.randomUUID().toString();
        var request = request(sku);

        var result = mockMvc.perform(post(BASE_PATH).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sku").value(sku))
                .andExpect(jsonPath("$.name").value("Test Product"))
                .andExpect(jsonPath("$.description").value("Test description"))
                .andExpect(jsonPath("$.price").value(123.50))
                .andExpect(jsonPath("$.currency").value("MXN"))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        UUID id = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        productIds.add(id);
        var persisted = readProduct(id);
        assertThat(persisted.getSku()).isEqualTo(sku);
        assertThat(persisted.getName()).isEqualTo(request.name());
        assertThat(persisted.getDescription()).isEqualTo(request.description());
        assertThat(persisted.getPrice()).isEqualByComparingTo(request.price());
        assertThat(persisted.getCurrency()).isEqualTo(Currency.MXN);
        assertThat(persisted.getQuantity()).isEqualTo(10);
        assertThat(persisted.getActive()).isTrue();
    }

    @Test
    void shouldListPersistedProductsThroughHttpAndReturn200WithJsonArray() throws Exception {
        UUID first = persistProduct(true);
        UUID second = persistProduct(false);

        mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(first.toString(), second.toString())));
    }

    @Test
    void shouldGetPersistedProductThroughHttpAndReturn200WithMatchingJson() throws Exception {
        UUID id = persistProduct(true);
        var product = readProduct(id);

        mockMvc.perform(get(BASE_PATH + "/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.sku").value(product.getSku()))
                .andExpect(jsonPath("$.name").value("Test Product"))
                .andExpect(jsonPath("$.description").value("Test description"))
                .andExpect(jsonPath("$.price").value(123.50))
                .andExpect(jsonPath("$.currency").value("MXN"))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldChangePriceThroughHttpAndReturn200WithPersistedPrice() throws Exception {
        UUID id = persistProduct(true);

        mockMvc.perform(patch(BASE_PATH + "/{id}/price", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangePriceRequest(new BigDecimal("200.00")))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.price").value(200.00));

        assertThat(readProduct(id).getPrice()).isEqualByComparingTo("200.00");
    }

    @Test
    void shouldIncreaseStockThroughHttpAndReturn200WithPersistedQuantity() throws Exception {
        UUID id = persistProduct(true);

        mockMvc.perform(patch(BASE_PATH + "/{id}/increase-stock", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new IncreaseStockRequest(3L))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.quantity").value(13));

        assertThat(readProduct(id).getQuantity()).isEqualTo(13L);
    }

    @Test
    void shouldDecreaseStockThroughHttpAndReturn200WithPersistedQuantity() throws Exception {
        UUID id = persistProduct(true);

        mockMvc.perform(patch(BASE_PATH + "/{id}/decrease-stock", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new DecreaseStockRequest(3L))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.quantity").value(7));

        assertThat(readProduct(id).getQuantity()).isEqualTo(7L);
    }

    @Test
    void shouldChangeCurrencyThroughHttpAndReturn200WithPersistedCurrency() throws Exception {
        UUID id = persistProduct(true);

        mockMvc.perform(patch(BASE_PATH + "/{id}/currency", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangeCurrencyRequest(Currency.USD))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.currency").value("USD"));

        assertThat(readProduct(id).getCurrency()).isEqualTo(Currency.USD);
    }

    @Test
    void shouldChangeNameThroughHttpAndReturn200WithPersistedName() throws Exception {
        UUID id = persistProduct(true);

        mockMvc.perform(patch(BASE_PATH + "/{id}/name", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangeNameRequest("New name"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("New name"));

        assertThat(readProduct(id).getName()).isEqualTo("New name");
    }

    @Test
    void shouldChangeDescriptionThroughHttpAndReturn200WithPersistedDescription() throws Exception {
        UUID id = persistProduct(true);

        mockMvc.perform(patch(BASE_PATH + "/{id}/description", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangeDescriptionRequest("New description"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.description").value("New description"));

        assertThat(readProduct(id).getDescription()).isEqualTo("New description");
    }

    @Test
    void shouldActivateProductThroughHttpAndReturn200WithPersistedState() throws Exception {
        UUID id = persistProduct(false);

        mockMvc.perform(patch(BASE_PATH + "/{id}/activate", id))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.active").value(true));

        assertThat(readProduct(id).getActive()).isTrue();
    }

    @Test
    void shouldDeactivateProductThroughHttpAndReturn200WithPersistedState() throws Exception {
        UUID id = persistProduct(true);

        mockMvc.perform(patch(BASE_PATH + "/{id}/deactivate", id))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.active").value(false));

        assertThat(readProduct(id).getActive()).isFalse();
    }

    @Test
    void shouldDeleteProductThroughHttpAndReturn204WithEmptyBodyAndPersistedDeletion() throws Exception {
        UUID id = persistProduct(true);
        mockMvc.perform(delete(BASE_PATH + "/{id}", id))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        assertThat(productRepository.findById(id)).isEmpty();
    }

    @Test
    void shouldRejectInvalidCreateRequestWith400AndErrorJsonWithoutPersistingProduct() throws Exception {
        long initialCount = productRepository.count();
        mockMvc.perform(post(BASE_PATH).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request(" "))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("SKU must not be blank."))
                .andExpect(jsonPath("$.path").value(BASE_PATH));
        assertThat(productRepository.count()).isEqualTo(initialCount);
    }

    @Test
    void shouldReturn404WithErrorJsonWhenProductDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get(BASE_PATH + "/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Product could not be found."))
                .andExpect(jsonPath("$.path").value(BASE_PATH + "/" + id));
    }

    @Test
    void shouldReturn409WithErrorJsonWhenCreatingProductWithDuplicateSku() throws Exception {
        UUID id = persistProduct(true);
        String sku = readProduct(id).getSku();
        long initialCount = productRepository.count();

        mockMvc.perform(post(BASE_PATH).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request(sku))))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Product must have unique SKU."));

        assertThat(productRepository.count()).isEqualTo(initialCount);
        assertThat(readProduct(id).getSku()).isEqualTo(sku);
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
