package com.orders.messages.orders_demo.app.order.internal;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.order.api.CreateOrderRequest;
import com.orders.messages.orders_demo.app.order.api.OrderResponse;
import com.orders.messages.orders_demo.app.product.internal.ProductEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductRepository;

@AutoConfigureMockMvc
class OrderControllerIntegrationTest extends PostgresContainer {
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;

    private final List<UUID> orderIds = new ArrayList<>();
    private final List<UUID> orderItemsIds = new ArrayList<>();
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
            orderItemsIds.forEach(orderItemRepository::deleteById);
            orderItemRepository.flush();
            orderIds.forEach(orderRepository::deleteById);
            orderRepository.flush();
            customerIds.forEach(customerRepository::deleteById);
            customerRepository.flush();
            productIds.forEach(productRepository::deleteById);
            productRepository.flush();
        });
    }

    @Test
    void shouldCreateOrderThroughHttpAndReturn201WithPersistedCustomerCurrencyAndPendingStatus() throws Exception {
        UUID customerId = persistCustomer();
        CreateOrderRequest request = createValidRequest(customerId);

        MvcResult resultMvc = mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.amountTotal").value(BigDecimal.ZERO))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andReturn();
        OrderResponse response = objectMapper.readValue(resultMvc.getResponse().getContentAsString(),
                OrderResponse.class);
        orderIds.add(response.id());

        transactionTemplate.executeWithoutResult((action) -> {
            OrderEntity orderEntity = orderRepository.findById(response.id()).orElseThrow();

            assertThat(orderEntity.getCustomerId()).isEqualTo(customerId);
            assertThat(orderEntity.getAmountTotal()).isEqualTo(new BigDecimal("0.00"));
            assertThat(orderEntity.getItems()).isEmpty();
            assertThat(orderEntity.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(orderEntity.getCurrency()).isEqualTo(Currency.MXN);
            assertThat(orderEntity.getCreatedAt()).isNotNull();
            assertThat(orderEntity.getVersion()).isNotNull();
        });
    }

    @Test
    void shouldReturn201Then200WithSameOrderForRepeatedCreationThroughHttp() throws Exception {
        UUID customerId = persistCustomer();
        var request = createValidRequest(customerId);

        var first = mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn();
        UUID id = objectMapper.readValue(first.getResponse().getContentAsString(), OrderResponse.class).id();
        orderIds.add(id);

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id.toString()));

        assertThat(orderRepository.findByCustomerId(customerId, org.springframework.data.domain.Pageable.unpaged())).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = { "PENDING_PAYMENT", "PAYMENT_IN_PROGRESS" })
    void shouldReturn200WithExistingOpenOrderThroughHttp(OrderStatus state) throws Exception {
        UUID customerId = persistCustomer();
        UUID id = persistOrder(customerId, state);
        UUID itemId = persistOrderItem(id, "SKU-OPEN", 2L);

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createValidRequest(customerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value(state.name()))
                .andExpect(jsonPath("$.amountTotal").value(246.00));

        transactionTemplate.executeWithoutResult(status -> {
            var orders = orderRepository.findByCustomerId(customerId, org.springframework.data.domain.Pageable.unpaged());
            assertThat(orders).hasSize(1);
            assertThat(orders.getContent().get(0).getItems()).extracting(OrderItemEntity::getId).containsExactly(itemId);
        });
    }

    @Test
    void shouldReturn409ForDifferentCurrencyWithoutCreatingAnotherOrderThroughHttp() throws Exception {
        UUID customerId = persistCustomer();
        UUID id = persistOrder(customerId);
        var request = CreateOrderRequest.builder().customerId(customerId).currency(Currency.USD).build();

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The open order uses a different currency."));

        var persisted = orderRepository.findByCustomerId(customerId, org.springframework.data.domain.Pageable.unpaged());
        assertThat(persisted).hasSize(1);
        assertThat(persisted.getContent().get(0).getId()).isEqualTo(id);
        assertThat(persisted.getContent().get(0).getCurrency()).isEqualTo(Currency.MXN);
    }

    @Test
    void shouldListPersistedOrdersThroughHttpAndReturn200WithJsonArray() throws Exception {
        UUID customerId_1 = persistCustomer();
        UUID customerId_2 = persistCustomer();
        UUID customerId_3 = persistCustomer();
        UUID orderId_1 = persistOrder(customerId_1);
        UUID orderId_2 = persistOrder(customerId_2);
        UUID orderId_3 = persistOrder(customerId_3);

        MvcResult resultMvc = mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        List<OrderResponse> responseList = objectMapper.readValue(resultMvc.getResponse().getContentAsString(),
                new TypeReference<List<OrderResponse>>() {
                });

        assertThat(responseList).hasSize(3);
        assertThat(responseList).extracting(OrderResponse::id)
                .containsExactlyInAnyOrder(orderId_1, orderId_2, orderId_3);
    }

    @Test
    void shouldGetPersistedOrderThroughHttpAndReturn200WithMatchingJson() throws Exception {
        UUID customerId = persistCustomer();
        UUID orderId = persistOrder(customerId);

        OrderResponse response = objectMapper.readValue(
                mockMvc.perform(get("/api/v1/orders/{id}", orderId))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                OrderResponse.class);

        assertThat(response.id()).isEqualTo(orderId);
        assertThat(response.customerId()).isEqualTo(customerId);
    }

    @Test
    void shouldCancelOrderThroughHttpAndReturn200WithPersistedStatusAndRestoredStock() throws Exception {
        Long itemsQuantity = 2L;
        UUID productId = persistProduct(INITIAL_STOCK);
        UUID customerId = persistCustomer();
        UUID orderId = persistOrder(customerId);
        persistOrderItem(orderId, PRODUCT_SKU, itemsQuantity);

        OrderResponse response = objectMapper.readValue(mockMvc.perform(patch("/api/v1/orders/{id}/cancel", orderId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andReturn()
                .getResponse()
                .getContentAsString(),
                OrderResponse.class);

        transactionTemplate.executeWithoutResult((action) -> {
            OrderEntity orderEntity = orderRepository.findById(response.id()).orElseThrow();
            ProductEntity productEntity = productRepository.findById(productId).orElseThrow();

            assertThat(orderEntity.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(productEntity.getQuantity()).isEqualTo(INITIAL_STOCK + itemsQuantity);
        });
    }

    @Test
    void shouldExpireOrderThroughHttpAndReturn200WithPersistedStatusAndRestoredStock() throws Exception {
        Long itemsQuantity = 2L;
        UUID productId = persistProduct(INITIAL_STOCK);
        UUID customerId = persistCustomer();
        UUID orderId = persistOrder(customerId);
        persistOrderItem(orderId, PRODUCT_SKU, itemsQuantity);

        OrderResponse response = objectMapper.readValue(mockMvc.perform(patch("/api/v1/orders/{id}/expire", orderId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andReturn()
                .getResponse()
                .getContentAsString(), OrderResponse.class);

        transactionTemplate.executeWithoutResult((action) -> {
            OrderEntity orderEntity = orderRepository.findById(response.id()).orElseThrow();
            ProductEntity productEntity = productRepository.findById(productId).orElseThrow();

            assertThat(orderEntity.getStatus()).isEqualTo(OrderStatus.EXPIRED);
            assertThat(productEntity.getQuantity()).isEqualTo(INITIAL_STOCK + itemsQuantity);
        });
    }

    @Test
    void shouldRefundPaidOrderThroughHttpAndReturn200WithPersistedStatusAndRestoredStock() throws Exception {
        Long itemsQuantity = 2L;
        UUID productId = persistProduct(INITIAL_STOCK);
        UUID customerId = persistCustomer();
        UUID orderId = persistOrder(customerId, OrderStatus.PAID);
        persistOrderItem(orderId, PRODUCT_SKU, itemsQuantity);

        OrderResponse response = objectMapper.readValue(mockMvc.perform(patch("/api/v1/orders/{id}/refund", orderId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andReturn()
                .getResponse()
                .getContentAsString(), OrderResponse.class);

        transactionTemplate.executeWithoutResult((action) -> {
            OrderEntity orderEntity = orderRepository.findById(response.id()).orElseThrow();
            ProductEntity productEntity = productRepository.findById(productId).orElseThrow();

            assertThat(orderEntity.getStatus()).isEqualTo(OrderStatus.REFUNDED);
            assertThat(productEntity.getQuantity()).isEqualTo(INITIAL_STOCK + itemsQuantity);
        });
    }

    @Test
    void shouldRejectInvalidCreateRequestWith400AndErrorJsonWithoutPersistingOrder() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(null)
                .currency(Currency.MXN)
                .build();

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));

        transactionTemplate.executeWithoutResult((action) -> {
            assertThat(orderRepository.findAll()).isEmpty();
        });
    }

    @Test
    void shouldReturn404WithErrorJsonWhenRequestedOrderDoesNotExist() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void shouldReturn409WithErrorJsonWhenCancellingPaidOrderWithoutChangingPersistedState() throws Exception {
        UUID customerId = persistCustomer();
        UUID orderId = persistOrder(customerId, OrderStatus.PAID);

        mockMvc.perform(patch("/api/v1/orders/{id}/cancel", orderId))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));

        transactionTemplate.executeWithoutResult((action) -> {
            OrderEntity orderEntity = orderRepository.findById(orderId).orElseThrow();

            assertThat(orderEntity.getStatus()).isEqualTo(OrderStatus.PAID);
        });
    }

    private UUID persistCustomer() {
        return transactionTemplate.execute(status -> {
            UUID customerId = customerRepository
                    .saveAndFlush(new CustomerEntity(UUID.randomUUID() + "@example.com", "name"))
                    .getId();

            customerIds.add(customerId);

            return customerId;
        });
    }

    private UUID persistProduct(Long stock) {
        return transactionTemplate.execute(status -> {
            UUID productId = productRepository
                    .saveAndFlush(ProductEntity.builder()
                            .name(PRODUCT_NAME)
                            .sku(PRODUCT_SKU)
                            .description(DEFAULT_DESCRIPTION)
                            .price(DEFAULT_UNIT_PRICE)
                            .quantity(stock)
                            .build())
                    .getId();

            productIds.add(productId);

            return productId;
        });
    }

    private UUID persistOrder(UUID customerId) {

        return transactionTemplate.execute(status -> {
            var savedID = orderRepository.saveAndFlush(
                    OrderEntity.builder()
                            .customerId(customerId)
                            .build())
                    .getId();

            orderIds.add(savedID);

            return savedID;
        });
    }

    private UUID persistOrder(UUID customerId, OrderStatus orderStatus) {

        return transactionTemplate.execute(status -> {
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

    private UUID persistOrderItem(UUID orderId, String sku, Long quantity) {
        return transactionTemplate.execute(status -> {

            var savedOrder = orderRepository.findById(orderId).orElseThrow();

            OrderItemEntity orderItemEntity = OrderItemEntity.builder()
                    .sku(sku)
                    .description(DEFAULT_DESCRIPTION)
                    .quantity(quantity)
                    .unitPrice(DEFAULT_UNIT_PRICE)
                    .build();

            savedOrder.addItem(orderItemEntity);

            orderRepository.saveAndFlush(savedOrder);

            entityManager.clear();

            savedOrder = orderRepository.findById(orderId).orElseThrow();

            UUID orderItemId = savedOrder.getItems().stream().filter(item -> item.getSku().equals(sku))
                    .findFirst().orElseThrow().getId();

            orderItemsIds.add(orderItemId);

            return orderItemId;
        });
    }

    @Test
    void shouldRejectOrderCreationForBlockedCustomerWithoutPersistingOrder() throws Exception {
        UUID customerId = persistCustomer();
        blockCustomer(customerId);

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createValidRequest(customerId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Blocked customers cannot create or reuse orders."));

        assertThat(orderRepository.findByCustomerIdAndStatusIn(customerId,
                List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_IN_PROGRESS))).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = { "PENDING_PAYMENT", "PAYMENT_IN_PROGRESS" })
    void shouldRejectReusingOpenOrderForBlockedCustomerWithoutChangingIt(OrderStatus state) throws Exception {
        UUID customerId = persistCustomer();
        UUID orderId = persistOrder(customerId, state);
        Long version = orderRepository.findById(orderId).orElseThrow().getVersion();
        blockCustomer(customerId);

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createValidRequest(customerId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Blocked customers cannot create or reuse orders."));

        var order = orderRepository.findByCustomerIdAndStatusIn(customerId,
                List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_IN_PROGRESS)).orElseThrow();
        assertThat(order.getId()).isEqualTo(orderId);
        assertThat(order.getStatus()).isEqualTo(state);
        assertThat(order.getVersion()).isEqualTo(version);
    }

    private void blockCustomer(UUID customerId) {
        transactionTemplate.executeWithoutResult(status -> {
            var customer = customerRepository.findById(customerId).orElseThrow();
            customer.deactivate();
            customerRepository.saveAndFlush(customer);
        });
    }

    private static CreateOrderRequest createValidRequest(UUID customerId) {
        return CreateOrderRequest.builder()
                .customerId(customerId)
                .currency(Currency.MXN)
                .build();
    }
}
