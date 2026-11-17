package com.orders.messages.orders_demo.app.order.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.order.api.CreateOrderItemRequest;
import com.orders.messages.orders_demo.app.order.api.OrderItemChangeQuantityRequest;
import com.orders.messages.orders_demo.app.order.api.OrderItemChangeUnitPriceRequest;
import com.orders.messages.orders_demo.app.product.internal.ProductEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductRepository;

@AutoConfigureMockMvc
class OrderItemControllerIntegrationTest extends PostgresContainer {

    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> orderIds = new ArrayList<>();
    private final List<UUID> customerIds = new ArrayList<>();
    private final List<UUID> productIds = new ArrayList<>();

    private static final String DEFAULT_DESCRIPTION = "description";
    private static final BigDecimal DEFAULT_UNIT_PRICE = new BigDecimal("123.00");
    private static final String PRODUCT_SKU = "SKU-123";
    private static final String PRODUCT_NAME = "Product Name";
    private static final Long INITIAL_STOCK = 5L;

    @AfterEach
    private void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            orderIds.forEach(orderRepository::deleteById);
            orderRepository.flush();
            customerIds.forEach(customerRepository::deleteById);
            customerRepository.flush();
            productIds.forEach(productRepository::deleteById);
            productRepository.flush();
        });
    }

    @Test
    void shouldCreateItemThroughHttpAndReturn201WithPersistedItemStockAndOrderTotal() throws Exception {
        Long requestedQuantity = 1l;
        UUID orderId = persistOrder();
        CreateOrderItemRequest itemRequest = createOrderItemRequest(requestedQuantity);

        mockMvc.perform(post("/api/v1/orders/{orderId}/items", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(itemRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.sku").value(PRODUCT_SKU))
                .andExpect(jsonPath("$.quantity").value(requestedQuantity));

        transactionTemplate.executeWithoutResult((context) -> {
            OrderEntity persisted = orderRepository.findById(orderId).orElseThrow();
            ProductEntity productEntity = productRepository.findBySku(PRODUCT_SKU).orElseThrow();

            assertThat(persisted.getAmountTotal()).isEqualTo(new BigDecimal("123.00"));
            assertThat(persisted.getItems()).hasSize(1);
            assertThat(persisted.getItems().get(0))
                    .extracting(OrderItemEntity::getSku, OrderItemEntity::getDescription, OrderItemEntity::getUnitPrice,
                            OrderItemEntity::getQuantity)
                    .containsExactly(PRODUCT_SKU, DEFAULT_DESCRIPTION, DEFAULT_UNIT_PRICE, requestedQuantity);
            assertThat(productEntity.getQuantity()).isEqualTo(INITIAL_STOCK - requestedQuantity);
        });
    }

    @Test
    void shouldIncreaseExistingItemQuantityWhenAddingSameSkuThroughHttp() throws Exception {
        Long requestedQuantity = 1L;
        Long initialOrderItemQuantity = 1L;
        UUID orderId = persistOrderWithOneItem(initialOrderItemQuantity);
        CreateOrderItemRequest itemRequest = createOrderItemRequest(requestedQuantity);

        mockMvc.perform(post("/api/v1/orders/{orderId}/items", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(itemRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.sku").value(PRODUCT_SKU))
                .andExpect(jsonPath("$.quantity").value(requestedQuantity + initialOrderItemQuantity));

        transactionTemplate.executeWithoutResult((context) -> {
            OrderEntity persisted = orderRepository.findById(orderId).orElseThrow();
            ProductEntity productEntity = productRepository.findBySku(PRODUCT_SKU).orElseThrow();

            assertThat(persisted.getAmountTotal()).isEqualTo(new BigDecimal("246.00"));
            assertThat(persisted.getItems()).hasSize(1);
            assertThat(persisted.getItems().get(0))
                    .extracting(
                            OrderItemEntity::getSku,
                            OrderItemEntity::getDescription,
                            OrderItemEntity::getUnitPrice,
                            OrderItemEntity::getQuantity)
                    .containsExactly(
                            PRODUCT_SKU,
                            DEFAULT_DESCRIPTION,
                            DEFAULT_UNIT_PRICE,
                            initialOrderItemQuantity + requestedQuantity);
            assertThat(productEntity.getQuantity())
                    .isEqualTo(INITIAL_STOCK - requestedQuantity - initialOrderItemQuantity);
        });
    }

    @Test
    void shouldListPersistedItemsThroughHttpAndReturn200WithJsonArray() throws Exception {
        Long initialOrderItemQuantity = 2L;
        UUID orderId = persistOrderWithOneItem(initialOrderItemQuantity);

        mockMvc.perform(get("/api/v1/orders/{orderId}/items", orderId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sku").value(PRODUCT_SKU))
                .andExpect(jsonPath("$[0].unitPrice").value(123.00))
                .andExpect(jsonPath("$[0].quantity").value(initialOrderItemQuantity));

        transactionTemplate.executeWithoutResult((context) -> {
            OrderEntity persisted = orderRepository.findById(orderId).orElseThrow();

            assertThat(persisted.getItems()).hasSize(1);
            assertThat(persisted.getAmountTotal()).isEqualTo(new BigDecimal("246.00"));
        });
    }

    @Test
    void shouldGetPersistedItemThroughHttpAndReturn200WithMatchingJson() throws Exception {
        Long initialOrderItemQuantity = 2L;
        UUID orderId = persistOrderWithOneItem(initialOrderItemQuantity);
        UUID orderItemId = getFirstOrderItem(orderId);

        mockMvc.perform(get("/api/v1/orders/{orderId}/items/{orderItemId}", orderId, orderItemId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sku").value(PRODUCT_SKU))
                .andExpect(jsonPath("$.unitPrice").value(123.00))
                .andExpect(jsonPath("$.quantity").value(initialOrderItemQuantity));

        transactionTemplate.executeWithoutResult((context) -> {
            OrderItemEntity persisted = orderItemRepository.findById(orderItemId).orElseThrow();

            assertThat(persisted.getLineTotal()).isEqualTo(new BigDecimal("246.00"));
            assertThat(persisted.getQuantity()).isEqualTo(initialOrderItemQuantity);
            assertThat(persisted.getUnitPrice()).isEqualTo(new BigDecimal("123.00"));
        });
    }

    @Test
    void shouldChangePriceThroughHttpAndReturn200WithPersistedTotals() throws Exception {
        Long initialOrderItemQuantity = 1L;
        BigDecimal newPrice = new BigDecimal("36.00");
        UUID orderId = persistOrderWithOneItem(initialOrderItemQuantity);
        UUID orderItemId = getFirstOrderItem(orderId);
        OrderItemChangeUnitPriceRequest request = new OrderItemChangeUnitPriceRequest(newPrice);

        mockMvc.perform(patch("/api/v1/orders/{orderId}/items/{orderItemId}/price", orderId, orderItemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sku").value(PRODUCT_SKU))
                .andExpect(jsonPath("$.unitPrice").value(36))
                .andExpect(jsonPath("$.quantity").value(1));

        transactionTemplate.executeWithoutResult((context) -> {
            OrderItemEntity persisted = orderItemRepository.findById(orderItemId).orElseThrow();

            assertThat(orderRepository.findById(orderId).orElseThrow().getAmountTotal())
                    .isEqualByComparingTo(newPrice);
            assertThat(productRepository.findBySku(PRODUCT_SKU).orElseThrow().getQuantity())
                    .isEqualTo(INITIAL_STOCK - initialOrderItemQuantity);
            assertThat(persisted.getLineTotal()).isEqualTo(newPrice);
            assertThat(persisted.getQuantity()).isEqualTo(1L);
            assertThat(persisted.getUnitPrice()).isEqualTo(newPrice);
        });
    }

    @Test
    void shouldChangeQuantityThroughHttpAndReturn200WithPersistedStockAndTotals() throws Exception {
        Long initialOrderItemQuantity = 1L;
        Long newQuantity = 3L;
        UUID orderId = persistOrderWithOneItem(initialOrderItemQuantity);
        UUID orderItemId = getFirstOrderItem(orderId);
        OrderItemChangeQuantityRequest request = new OrderItemChangeQuantityRequest(newQuantity);

        mockMvc.perform(patch("/api/v1/orders/{orderId}/items/{orderItemId}/quantity", orderId, orderItemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sku").value(PRODUCT_SKU))
                .andExpect(jsonPath("$.unitPrice").value(123))
                .andExpect(jsonPath("$.quantity").value(newQuantity));

        transactionTemplate.executeWithoutResult((context) -> {
            OrderItemEntity persisted = orderItemRepository.findById(orderItemId).orElseThrow();

            assertThat(orderRepository.findById(orderId).orElseThrow().getAmountTotal())
                    .isEqualByComparingTo("369.00");
            assertThat(productRepository.findBySku(PRODUCT_SKU).orElseThrow().getQuantity())
                    .isEqualTo(INITIAL_STOCK - newQuantity);
            assertThat(persisted.getLineTotal()).isEqualTo(new BigDecimal("369.00"));
            assertThat(persisted.getQuantity()).isEqualTo(newQuantity);
            assertThat(persisted.getUnitPrice()).isEqualTo(DEFAULT_UNIT_PRICE);
        });
    }

    @Test
    void shouldDeleteItemThroughHttpAndReturn204WithEmptyBodyAndPersistedChanges() throws Exception {
        Long initialOrderItemQuantity = 1L;
        UUID orderId = persistOrderWithOneItem(initialOrderItemQuantity);
        UUID orderItemId = getFirstOrderItem(orderId);

        mockMvc.perform(delete("/api/v1/orders/{orderId}/items/{orderItemId}", orderId, orderItemId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        transactionTemplate.executeWithoutResult((context) -> {
            OrderEntity persistedOrder = orderRepository.findById(orderId).orElseThrow();

            assertThat(productRepository.findBySku(PRODUCT_SKU).orElseThrow().getQuantity())
                    .isEqualTo(INITIAL_STOCK);
            assertThat(orderItemRepository.findById(orderItemId)).isEmpty();
            assertThat(persistedOrder.getItems()).extracting("id").doesNotContain(orderItemId);
            assertThat(persistedOrder.getAmountTotal()).isEqualTo(new BigDecimal("0.00"));
        });
    }

    @Test
    void shouldRejectInvalidCreateRequestWith400AndErrorJsonWithoutPersistedChanges() throws Exception {
        Long initialOrderItemQuantity = 1L;
        UUID orderId = persistOrderWithOneItem(initialOrderItemQuantity);
        CreateOrderItemRequest request = new CreateOrderItemRequest("", 20L);
        UUID orderItemId = getFirstOrderItem(orderId);

        mockMvc.perform(post("/api/v1/orders/{orderId}/items", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId + "/items"));

        transactionTemplate.executeWithoutResult((context) -> {
            OrderItemEntity persisted = orderItemRepository.findById(orderItemId).orElseThrow();
            OrderEntity persistedOrder = orderRepository.findById(orderId).orElseThrow();

            assertThat(productRepository.findBySku(PRODUCT_SKU).orElseThrow().getQuantity())
                    .isEqualTo(INITIAL_STOCK - initialOrderItemQuantity);
            assertThat(persistedOrder.getItems()).hasSize(1);
            assertThat(persistedOrder.getAmountTotal()).isEqualTo(new BigDecimal("123.00"));
            assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(persisted.getLineTotal()).isEqualTo(new BigDecimal("123.00"));
            assertThat(persisted.getUnitPrice()).isEqualTo(new BigDecimal("123.00"));
            assertThat(persisted.getQuantity()).isEqualTo(1L);
        });
    }

    @Test
    void shouldReturn404WithErrorJsonWhenRequestedItemDoesNotExist() throws Exception {
        UUID orderId = persistOrder();
        UUID missingItemId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/orders/{orderId}/items/{orderItemId}", orderId, missingItemId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId + "/items/" + missingItemId));
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "PENDING_PAYMENT")
    void shouldReturn409WithErrorJsonWhenCreatingItemForNonPendingOrder(OrderStatus orderStatus) throws Exception {
        UUID orderId = persistOrder(orderStatus);
        CreateOrderItemRequest request = new CreateOrderItemRequest(PRODUCT_SKU, 20L);

        mockMvc.perform(post("/api/v1/orders/{orderId}/items", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId + "/items"));
    }

    private static CreateOrderItemRequest createOrderItemRequest(Long quantity) {
        return new CreateOrderItemRequest(
                PRODUCT_SKU,
                quantity);
    }

    private UUID persistOrder() {
        return transactionTemplate.execute(status -> {
            UUID productId = productRepository
                    .saveAndFlush(ProductEntity.builder()
                            .name(PRODUCT_NAME)
                            .sku(PRODUCT_SKU)
                            .description(DEFAULT_DESCRIPTION)
                            .price(DEFAULT_UNIT_PRICE)
                            .quantity(INITIAL_STOCK)
                            .build())
                    .getId();

            productIds.add(productId);

            UUID customerId = customerRepository
                    .saveAndFlush(new CustomerEntity(UUID.randomUUID() + "@example.com", "name"))
                    .getId();

            customerIds.add(customerId);

            var savedID = orderRepository.saveAndFlush(
                    OrderEntity.builder()
                            .customerId(customerId)
                            .build())
                    .getId();

            orderIds.add(savedID);

            return savedID;
        });
    }

    private UUID persistOrder(OrderStatus orderStatus) {
        return transactionTemplate.execute(status -> {
            UUID productId = productRepository
                    .saveAndFlush(ProductEntity.builder()
                            .name(PRODUCT_NAME)
                            .sku(PRODUCT_SKU)
                            .description(DEFAULT_DESCRIPTION)
                            .price(DEFAULT_UNIT_PRICE)
                            .quantity(INITIAL_STOCK)
                            .build())
                    .getId();

            productIds.add(productId);

            UUID customerId = customerRepository
                    .saveAndFlush(new CustomerEntity(UUID.randomUUID() + "@example.com", "name"))
                    .getId();

            customerIds.add(customerId);

            var savedID = orderRepository.saveAndFlush(
                    OrderEntity.builder()
                            .status(orderStatus)
                            .customerId(customerId)
                            .build())
                    .getId();

            orderIds.add(savedID);

            return savedID;
        });
    }

    private UUID persistOrderWithOneItem(long quantity) {
        return transactionTemplate.execute(status -> {
            UUID productId = productRepository
                    .saveAndFlush(ProductEntity.builder()
                            .name(PRODUCT_NAME)
                            .sku(PRODUCT_SKU)
                            .description(DEFAULT_DESCRIPTION)
                            .price(DEFAULT_UNIT_PRICE)
                            .quantity(INITIAL_STOCK - quantity)
                            .build())
                    .getId();

            productIds.add(productId);

            UUID customerId = customerRepository
                    .saveAndFlush(new CustomerEntity(UUID.randomUUID() + "@example.com", "name"))
                    .getId();

            customerIds.add(customerId);

            var savedID = orderRepository.saveAndFlush(
                    OrderEntity.builder()
                            .customerId(customerId)
                            .addItem(OrderItemEntity.builder()
                                    .sku(PRODUCT_SKU)
                                    .description(DEFAULT_DESCRIPTION)
                                    .unitPrice(DEFAULT_UNIT_PRICE)
                                    .quantity(quantity)
                                    .build())
                            .build())
                    .getId();

            orderIds.add(savedID);

            return savedID;
        });
    }

    private UUID getFirstOrderItem(UUID orderId) {
        return transactionTemplate.execute(action -> {
            return orderRepository.findById(orderId).orElseThrow().getItems().getFirst().getId();
        });
    }
}
