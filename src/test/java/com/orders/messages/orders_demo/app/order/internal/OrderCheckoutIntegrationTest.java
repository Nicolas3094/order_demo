package com.orders.messages.orders_demo.app.order.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.customer.api.CreateCustomerRequest;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.order.api.CreateOrderItemRequest;
import com.orders.messages.orders_demo.app.order.api.CreateOrderRequest;
import com.orders.messages.orders_demo.app.order.api.OrderItemResponse;
import com.orders.messages.orders_demo.app.order.api.OrderResponse;
import com.orders.messages.orders_demo.app.payment.api.CreatePaymentAttemptRequest;
import com.orders.messages.orders_demo.app.payment.api.PaymentAttemptResponse;
import com.orders.messages.orders_demo.app.payment.api.PaymentSucceededRequest;
import com.orders.messages.orders_demo.app.payment.api.PaymentFailedRequest;
import com.orders.messages.orders_demo.app.payment.internal.PaymentAttemptRepository;
import com.orders.messages.orders_demo.app.payment.internal.PaymentProvider;
import com.orders.messages.orders_demo.app.payment.internal.PaymentStatus;
import com.orders.messages.orders_demo.app.product.api.CreateProductRequest;
import com.orders.messages.orders_demo.app.product.api.ProductResponse;
import com.orders.messages.orders_demo.app.product.internal.ProductRepository;

@AutoConfigureMockMvc
class OrderCheckoutIntegrationTest extends PostgresContainer {
    @Autowired
    private ObjectMapper objectMapper;
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
    private PaymentAttemptRepository paymentAttemptRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<UUID> orderIds = new ArrayList<>();
    private final List<UUID> orderItemsIds = new ArrayList<>();
    private final List<UUID> customerIds = new ArrayList<>();
    private final List<UUID> productIds = new ArrayList<>();
    private final List<UUID> paymentsIds = new ArrayList<>();

    private static final String CUSTOMER_BASE_PATH = "/api/v1/customers";
    private static final String PRODUCT_BASE_PATH = "/api/v1/products";
    private static final String ORDER_BASE_PATH = "/api/v1/orders";

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
            paymentsIds.forEach(paymentAttemptRepository::deleteById);
            paymentAttemptRepository.flush();
        });
        orderItemsIds.clear();
        orderIds.clear();
        customerIds.clear();
        productIds.clear();
        paymentsIds.clear();
    }

    @Test
    void shouldCompleteCheckoutThroughHttpWithSucceededPaymentPaidOrderAndCorrectTotalAndStock() throws Exception {
        final String idempotencyKey = "IDEMPOTENCY-KEY";
        final String sku = "SKU-123";

        UUID customerId = postCustomer();
        UUID productId = postProduct(sku, "123.00", 5L);
        UUID orderId = postOrder(customerId);
        addOrderItem(orderId, sku, 2L);
        UUID paymentId = postPaymentAttempt(orderId, idempotencyKey);
        startProcessingPayment(orderId, paymentId, idempotencyKey);
        markPaymentAsSucceeded(orderId, paymentId, idempotencyKey);

        transactionTemplate.executeWithoutResult(status -> {
            var persistedOrder = orderRepository.findById(orderId).orElseThrow();
            var persistedProduct = productRepository.findById(productId).orElseThrow();
            var persistedPayment = paymentAttemptRepository.findById(paymentId).orElseThrow();

            assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(persistedOrder.getAmountTotal()).isEqualByComparingTo("246.00");
            assertThat(persistedProduct.getQuantity()).isEqualTo(3L);
            assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
            assertThat(persistedPayment.getAmount()).isEqualByComparingTo("246.00");
            assertThat(persistedPayment.getIdempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(persistedPayment.getOrderId()).isEqualTo(orderId);
        });
    }

    @Test
    void shouldRejectAddingItemWithInsufficientStockThroughHttpWithoutChangingItemsOrderTotalOrStock()
            throws Exception {
        final String sku = "SKU-INSUFFICIENT";
        var request = new CreateOrderItemRequest(sku, 4L);

        UUID customerId = postCustomer();
        UUID productId = postProduct(sku, "123.00", 5L);
        UUID orderId = postOrder(customerId);
        OrderItemResponse existingItem = addOrderItem(orderId, sku, 2L);
        mockMvc.perform(post("/api/v1/orders/{orderId}/items", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Insufficient stock."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId + "/items"));

        transactionTemplate.executeWithoutResult(status -> {
            var persistedOrder = orderRepository.findById(orderId).orElseThrow();
            var persistedProduct = productRepository.findById(productId).orElseThrow();

            assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(persistedOrder.getAmountTotal()).isEqualByComparingTo("246.00");
            assertThat(persistedOrder.getItems()).hasSize(1);
            var persistedItem = persistedOrder.getItems().get(0);
            assertThat(persistedItem.getId()).isEqualTo(existingItem.id());
            assertThat(persistedItem.getSku()).isEqualTo(sku);
            assertThat(persistedItem.getQuantity()).isEqualTo(2L);
            assertThat(persistedItem.getUnitPrice()).isEqualByComparingTo("123.00");
            assertThat(persistedItem.getLineTotal()).isEqualByComparingTo("246.00");
            assertThat(persistedProduct.getQuantity()).isEqualTo(3L);
            assertThat(paymentAttemptRepository.findByOrderId(orderId)).isEmpty();
        });
    }

    @Test
    void shouldRecordFailedPaymentThroughHttpAndKeepOrderPendingWithReservedStockAndUnchangedTotal() throws Exception {
        final String sku = "SKU-FAILED-PAYMENT";
        final String idempotencyKey = UUID.randomUUID().toString();

        UUID customerId = postCustomer();
        UUID productId = postProduct(sku, "123.00", 5L);
        UUID orderId = postOrder(customerId);
        OrderItemResponse existingItem = addOrderItem(orderId, sku, 2L);
        UUID paymentId = postPaymentAttempt(orderId, idempotencyKey);
        startProcessingPayment(orderId, paymentId, idempotencyKey);
        var request = new PaymentFailedRequest(402, "Payment declined.");

        mockMvc.perform(patch("/api/v1/orders/{orderId}/payments/{paymentId}/failed", orderId, paymentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value(402))
                .andExpect(jsonPath("$.failureMessage").value("Payment declined."));

        transactionTemplate.executeWithoutResult(status -> {
            var persistedOrder = orderRepository.findById(orderId).orElseThrow();
            var persistedProduct = productRepository.findById(productId).orElseThrow();
            var persistedPayment = paymentAttemptRepository.findById(paymentId).orElseThrow();

            assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(persistedOrder.getAmountTotal()).isEqualByComparingTo("246.00");
            assertThat(persistedOrder.getItems()).hasSize(1);
            var persistedItem = persistedOrder.getItems().get(0);
            assertThat(persistedItem.getId()).isEqualTo(existingItem.id());
            assertThat(persistedItem.getQuantity()).isEqualTo(2L);
            assertThat(persistedItem.getLineTotal()).isEqualByComparingTo("246.00");
            assertThat(persistedProduct.getQuantity()).isEqualTo(3L);
            assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(persistedPayment.getFailureCode()).isEqualTo(402);
            assertThat(persistedPayment.getFailureMessage()).isEqualTo("Payment declined.");
            assertThat(persistedPayment.getAmount()).isEqualByComparingTo("246.00");
            assertThat(persistedPayment.getOrderId()).isEqualTo(orderId);
            assertThat(persistedPayment.getIdempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(persistedPayment.getProviderRef()).isNull();
            assertThat(paymentAttemptRepository.findByOrderId(orderId)).hasSize(1);
        });
    }

    @Test
    void shouldRejectPaymentForCancelledOrderThroughHttpWithoutCreatingPaymentOrChangingRestoredStock()
            throws Exception {
        final String sku = "SKU-CANCELLED-ORDER";

        UUID customerId = postCustomer();
        UUID productId = postProduct(sku, "123.00", 5L);
        UUID orderId = postOrder(customerId);
        OrderItemResponse existingItem = addOrderItem(orderId, sku, 2L);
        mockMvc.perform(patch("/api/v1/orders/{orderId}/cancel", orderId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(productRepository.findById(productId).orElseThrow().getQuantity()).isEqualTo(5L);
            assertThat(paymentAttemptRepository.findByOrderId(orderId)).isEmpty();
        });
        var request = new CreatePaymentAttemptRequest(PaymentProvider.NONE, UUID.randomUUID().toString());
        mockMvc.perform(post("/api/v1/orders/{orderId}/payments", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Only pending orders can be modified."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId + "/payments"));

        transactionTemplate.executeWithoutResult(status -> {
            var persistedOrder = orderRepository.findById(orderId).orElseThrow();

            assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(persistedOrder.getAmountTotal()).isEqualByComparingTo("246.00");
            assertThat(persistedOrder.getItems()).hasSize(1);
            var persistedItem = persistedOrder.getItems().get(0);
            assertThat(persistedItem.getId()).isEqualTo(existingItem.id());
            assertThat(persistedItem.getQuantity()).isEqualTo(2L);
            assertThat(persistedItem.getLineTotal()).isEqualByComparingTo("246.00");
            assertThat(productRepository.findById(productId).orElseThrow().getQuantity()).isEqualTo(5L);
            assertThat(paymentAttemptRepository.findByOrderId(orderId)).isEmpty();
            assertThat(paymentAttemptRepository.findByIdempotencyKey(request.idempotencyKey())).isEmpty();
        });
    }

    @Test
    void shouldRejectIdempotencyKeyFromAnotherOrderThroughHttpWith409WithoutCreatingOrChangingPayments() {
    }

    @Test
    void shouldRejectItemChangesAfterSuccessfulPaymentThroughHttpWithoutChangingItemsTotalStockOrPayment() {
    }

    private OrderItemResponse addOrderItem(UUID orderId, String sku, Long quantity) throws Exception {
        var request = new CreateOrderItemRequest(sku, quantity);

        var resultMvc = mockMvc.perform(post("/api/v1/orders/{orderId}/items", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sku").value(sku))
                .andExpect(jsonPath("$.quantity").value(quantity))
                .andReturn();

        OrderItemResponse response = objectMapper.readValue(resultMvc.getResponse().getContentAsString(),
                OrderItemResponse.class);

        orderItemsIds.add(response.id());

        return response;
    }

    private PaymentAttemptResponse markPaymentAsSucceeded(UUID orderId, UUID paymentId, String idempotencyKey)
            throws Exception {
        PaymentSucceededRequest paymentSucceededRequest = new PaymentSucceededRequest("DEFAULT_PROVIDER_REF");

        var resultMvc = mockMvc
                .perform(patch("/api/v1/orders/{orderId}/payments/{paymentId}/succeeded", orderId, paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentSucceededRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.provider").value("NONE"))
                .andExpect(jsonPath("$.idempotencyKey").value(idempotencyKey))
                .andExpect(jsonPath("$.status").value("SUCCEEDED")).andReturn();

        PaymentAttemptResponse response = objectMapper.readValue(resultMvc.getResponse().getContentAsString(),
                PaymentAttemptResponse.class);

        return response;
    }

    private PaymentAttemptResponse startProcessingPayment(UUID orderId, UUID paymentId, String idempotencyKey)
            throws Exception {
        var resultMvc = mockMvc
                .perform(patch("/api/v1/orders/{orderId}/payments/{paymentId}/processing", orderId, paymentId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.provider").value("NONE"))
                .andExpect(jsonPath("$.idempotencyKey").value(idempotencyKey))
                .andExpect(jsonPath("$.status").value("PROCESSING")).andReturn();

        PaymentAttemptResponse response = objectMapper.readValue(resultMvc.getResponse().getContentAsString(),
                PaymentAttemptResponse.class);

        return response;

    }

    private UUID postPaymentAttempt(UUID orderId, String idempotencyKey)
            throws Exception {
        CreatePaymentAttemptRequest paymentRequest = new CreatePaymentAttemptRequest(PaymentProvider.NONE,
                idempotencyKey);

        var resultMvc = mockMvc.perform(post("/api/v1/orders/{orderId}/payments", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("NONE"))
                .andExpect(jsonPath("$.idempotencyKey").value(idempotencyKey))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andReturn();

        PaymentAttemptResponse response = objectMapper.readValue(resultMvc.getResponse().getContentAsString(),
                PaymentAttemptResponse.class);

        paymentsIds.add(response.id());

        return response.id();
    }

    private UUID postOrder(UUID customerId) throws Exception {
        MvcResult resultMvc = mockMvc.perform(post(ORDER_BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createOrderRequest(customerId))))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.amountTotal").value(BigDecimal.ZERO))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andReturn();

        OrderResponse response = objectMapper.readValue(resultMvc.getResponse().getContentAsString(),
                OrderResponse.class);

        orderIds.add(response.id());

        return response.id();
    }

    private UUID postProduct(String sku, String unitPrice, Long stock) throws Exception {
        var productRequest = createProductRequest(sku, unitPrice, stock);

        var result = mockMvc.perform(post(PRODUCT_BASE_PATH).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sku").value(sku))
                .andReturn();

        var response = objectMapper.readValue(result.getResponse().getContentAsString(),
                ProductResponse.class);

        productIds.add(response.id());

        return response.id();
    }

    private UUID postCustomer() throws Exception {
        var request = new CreateCustomerRequest(UUID.randomUUID().toString() + "@email.com",
                "Test Customer");

        var result = mockMvc.perform(post(CUSTOMER_BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        UUID customerId = UUID
                .fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());

        customerIds.add(customerId);

        return customerId;
    }

    private CreateProductRequest createProductRequest(String sku, String unitPrice, Long stock) {
        return new CreateProductRequest(
                sku,
                UUID.randomUUID().toString() + "Product",
                UUID.randomUUID().toString(),
                new BigDecimal(unitPrice),
                Currency.MXN, true,
                stock);
    }

    private static CreateOrderRequest createOrderRequest(UUID customerId) {
        return CreateOrderRequest.builder()
                .customerId(customerId)
                .currency(Currency.MXN)
                .build();
    }

}
