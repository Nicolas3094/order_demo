package com.orders.messages.orders_demo.app.payment.internal;

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
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.product.internal.ProductEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductRepository;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.order.internal.*;
import com.orders.messages.orders_demo.app.payment.api.*;

@AutoConfigureMockMvc
class PaymentControllerIntegrationTest extends PostgresContainer {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private PaymentAttemptRepository paymentRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private CustomerRepository customerRepository;

    private final List<UUID> paymentIds = new ArrayList<>();
    private final List<UUID> orderIds = new ArrayList<>();
    private final List<UUID> customerIds = new ArrayList<>();
    private final List<UUID> reservationProductIds = new ArrayList<>();
    @Autowired
    private ProductRepository productRepository;
    private static final String BASE_PATH = "/api/v1/orders/{orderId}/payments";

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            paymentIds.forEach(paymentRepository::deleteById);
            paymentRepository.flush();
            orderIds.forEach(orderRepository::deleteById);
            orderRepository.flush();
            customerIds.forEach(customerRepository::deleteById);
            customerRepository.flush();
            reservationProductIds.forEach(productRepository::deleteById);
            productRepository.flush();
        });
        paymentIds.clear();
        orderIds.clear();
        customerIds.clear();
        reservationProductIds.clear();
    }

    @Test
    void shouldCreatePaymentThroughHttpAndReturn201WithPersistedOrderAmount() throws Exception {
        UUID orderId = persistOrder();
        String key = UUID.randomUUID().toString();
        UUID paymentId = createPaymentThroughHttp(orderId, key);

        transactionTemplate.executeWithoutResult(status -> {
            PaymentAttemptEntity payment = paymentRepository.findById(paymentId).orElseThrow();
            assertThat(payment.getOrderId()).isEqualTo(orderId);
            assertThat(payment.getAmount()).isEqualByComparingTo("247.00");
            assertThat(payment.getProvider()).isEqualTo(PaymentProvider.STRIPE);
            assertThat(payment.getIdempotencyKey()).isEqualTo(key);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CREATED);
        });
    }

    @Test
    void shouldReusePaymentThroughHttpForRepeatedIdempotencyKey() throws Exception {
        UUID orderId = persistOrder();
        String key = UUID.randomUUID().toString();
        UUID firstId = createPaymentThroughHttp(orderId, key);
        UUID repeatedId = createPaymentThroughHttp(orderId, key);

        assertThat(repeatedId).isEqualTo(firstId);
        assertThat(paymentRepository.findByOrderId(orderId)).hasSize(1);
    }

    @Test
    void shouldListPersistedPaymentsThroughHttpAndReturn200WithJsonArray() throws Exception {
        UUID orderId = persistOrder();
        UUID firstId = persistPayment(orderId, PaymentStatus.CREATED);
        UUID secondId = persistPayment(orderId, PaymentStatus.FAILED);
        persistPayment(persistOrder(), PaymentStatus.CREATED);

        mockMvc.perform(get(BASE_PATH, orderId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(firstId.toString(), secondId.toString())));
    }

    @Test
    void shouldGetPersistedPaymentThroughHttpAndReturn200WithMatchingJson() throws Exception {
        UUID orderId = persistOrder();
        UUID paymentId = persistPayment(orderId, PaymentStatus.CREATED);

        mockMvc.perform(get(BASE_PATH + "/{paymentId}", orderId, paymentId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.provider").value("STRIPE"))
                .andExpect(jsonPath("$.amount").value(247.00))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void shouldStartProcessingThroughHttpAndReturn200WithPersistedStatus() throws Exception {
        UUID orderId = persistOrder();
        transactionTemplate.executeWithoutResult(status -> {
            var order = orderRepository.findById(orderId).orElseThrow();
            order.paymentInProgress();
            orderRepository.saveAndFlush(order);
        });
        UUID paymentId = persistPayment(orderId, PaymentStatus.CREATED);

        mockMvc.perform(patch(BASE_PATH + "/{paymentId}/processing", orderId, paymentId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        transactionTemplate.executeWithoutResult(status -> {
            PaymentAttemptEntity payment = paymentRepository.findById(paymentId).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
            assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
        });
    }

    @Test
    void shouldMarkPaymentSucceededThroughHttpAndPersistPaymentAndOrderStatuses() throws Exception {
        UUID orderId = persistOrder();
        transactionTemplate.executeWithoutResult(status -> {
            var order = orderRepository.findById(orderId).orElseThrow();
            order.paymentInProgress();
            orderRepository.saveAndFlush(order);
        });
        UUID paymentId = persistPayment(orderId, PaymentStatus.PROCESSING);

        mockMvc.perform(patch(BASE_PATH + "/{paymentId}/succeeded", orderId, paymentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PaymentSucceededRequest("provider-ref-123"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.providerRef").value("provider-ref-123"));

        transactionTemplate.executeWithoutResult(status -> {
            PaymentAttemptEntity payment = paymentRepository.findById(paymentId).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
            assertThat(payment.getProviderRef()).isEqualTo("provider-ref-123");
            assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        });
    }

    @Test
    void shouldMarkPaymentFailedThroughHttpAndPersistFailureDetails() throws Exception {
        UUID orderId = persistOrder();
        transactionTemplate.executeWithoutResult(status -> {
            var order = orderRepository.findById(orderId).orElseThrow();
            order.paymentInProgress();
            orderRepository.saveAndFlush(order);
        });
        UUID paymentId = persistPayment(orderId, PaymentStatus.PROCESSING);

        mockMvc.perform(patch(BASE_PATH + "/{paymentId}/failed", orderId, paymentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PaymentFailedRequest(402, "Payment declined."))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value(402))
                .andExpect(jsonPath("$.failureMessage").value("Payment declined."));

        transactionTemplate.executeWithoutResult(status -> {
            PaymentAttemptEntity payment = paymentRepository.findById(paymentId).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailureCode()).isEqualTo(402);
            assertThat(payment.getFailureMessage()).isEqualTo("Payment declined.");
            assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PENDING_PAYMENT);
        });
    }

    @Test
    void shouldCancelPaymentThroughHttpAndPersistCancelledStatus() throws Exception {
        UUID orderId = persistOrder();
        transactionTemplate.executeWithoutResult(status -> {
            var order = orderRepository.findById(orderId).orElseThrow();
            order.paymentInProgress();
            orderRepository.saveAndFlush(order);
        });
        UUID paymentId = persistPayment(orderId, PaymentStatus.CREATED);

        mockMvc.perform(patch(BASE_PATH + "/{paymentId}/cancel", orderId, paymentId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        transactionTemplate.executeWithoutResult(status -> {
            PaymentAttemptEntity payment = paymentRepository.findById(paymentId).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PENDING_PAYMENT);
        });
    }

    @Test
    void shouldRejectInvalidPaymentRequestWith400AndErrorJsonWithoutPersistedChanges() throws Exception {
        UUID orderId = persistOrder();
        long initialCount = paymentRepository.count();

        mockMvc.perform(post(BASE_PATH, orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, " "))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Payment must have idempotency key."));

        assertThat(paymentRepository.count()).isEqualTo(initialCount);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void shouldReturn404WithErrorJsonWhenPaymentDoesNotExist() throws Exception {
        UUID orderId = persistOrder();

        mockMvc.perform(get(BASE_PATH + "/{paymentId}", orderId, UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Payment attempt not found."));
    }

    @Test
    void shouldReturn409WithErrorJsonForInvalidPaymentTransition() throws Exception {
        UUID orderId = persistOrder();
        transactionTemplate.executeWithoutResult(status -> {
            var order = orderRepository.findById(orderId).orElseThrow();
            order.paymentInProgress();
            orderRepository.saveAndFlush(order);
        });
        UUID paymentId = persistPayment(orderId, PaymentStatus.CANCELLED);

        mockMvc.perform(patch(BASE_PATH + "/{paymentId}/processing", orderId, paymentId))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Only created payments can start processing."));

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void shouldRejectNewPaymentForBlockedCustomerWith409WithoutChangingOrder() throws Exception {
        UUID orderId = persistOrder();
        blockCustomerForOrder(orderId);
        long initialCount = paymentRepository.count();
        var request = new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, UUID.randomUUID().toString());

        mockMvc.perform(post(BASE_PATH, orderId).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Blocked customers cannot start payments."));

        assertThat(paymentRepository.count()).isEqualTo(initialCount);
        assertThat(paymentRepository.findByOrderId(orderId)).isEmpty();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void shouldRejectProcessingWhenCustomerWasBlockedAfterPaymentCreation() throws Exception {
        UUID orderId = persistOrder();
        UUID paymentId = createPaymentThroughHttp(orderId, UUID.randomUUID().toString());
        blockCustomerForOrder(orderId);
        Long version = orderRepository.findById(orderId).orElseThrow().getVersion();

        mockMvc.perform(patch(BASE_PATH + "/{paymentId}/processing", orderId, paymentId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Blocked customers cannot start payments."));

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CREATED);
        var order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
        assertThat(order.getVersion()).isEqualTo(version);
    }

    @Test
    void shouldReuseExistingPaymentEvenIfCustomerWasBlockedAfterCreation() throws Exception {
        UUID orderId = persistOrder();
        String key = UUID.randomUUID().toString();
        UUID paymentId = createPaymentThroughHttp(orderId, key);
        blockCustomerForOrder(orderId);

        UUID repeatedId = createPaymentThroughHttp(orderId, key);

        assertThat(repeatedId).isEqualTo(paymentId);
        assertThat(paymentRepository.findByOrderId(orderId)).hasSize(1);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
    }

    @Test
    void shouldAllowSucceededNotificationForBlockedCustomerWithExistingPayment() throws Exception {
        UUID orderId = persistOrder();
        UUID paymentId = createPaymentThroughHttp(orderId, UUID.randomUUID().toString());
        mockMvc.perform(patch(BASE_PATH + "/{paymentId}/processing", orderId, paymentId))
                .andExpect(status().isOk());
        blockCustomerForOrder(orderId);

        mockMvc.perform(patch(BASE_PATH + "/{paymentId}/succeeded", orderId, paymentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PaymentSucceededRequest("blocked-customer-ref"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void shouldAllowFailedNotificationForBlockedCustomerWithExistingPayment() throws Exception {
        UUID orderId = persistOrder();
        UUID paymentId = createPaymentThroughHttp(orderId, UUID.randomUUID().toString());
        mockMvc.perform(patch(BASE_PATH + "/{paymentId}/processing", orderId, paymentId))
                .andExpect(status().isOk());
        blockCustomerForOrder(orderId);

        mockMvc.perform(patch(BASE_PATH + "/{paymentId}/failed", orderId, paymentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PaymentFailedRequest(402, "Declined"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void shouldAllowCancelledNotificationForBlockedCustomerWithExistingPayment() throws Exception {
        UUID orderId = persistOrder();
        UUID paymentId = createPaymentThroughHttp(orderId, UUID.randomUUID().toString());
        mockMvc.perform(patch(BASE_PATH + "/{paymentId}/processing", orderId, paymentId))
                .andExpect(status().isOk());
        blockCustomerForOrder(orderId);

        mockMvc.perform(patch(BASE_PATH + "/{paymentId}/cancel", orderId, paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    private void blockCustomerForOrder(UUID orderId) {
        transactionTemplate.executeWithoutResult(status -> {
            UUID customerId = orderRepository.findById(orderId).orElseThrow().getCustomerId();
            var customer = customerRepository.findById(customerId).orElseThrow();
            customer.deactivate();
            customerRepository.saveAndFlush(customer);
        });
    }

    private UUID createPaymentThroughHttp(UUID orderId, String key) throws Exception {
        var result = mockMvc.perform(post(BASE_PATH, orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, key))))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.amount").value(247.00))
                .andExpect(jsonPath("$.provider").value("STRIPE"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.idempotencyKey").value(key))
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        if (!paymentIds.contains(id)) {
            paymentIds.add(id);
        }
        return id;
    }

    @Test
    void shouldRejectPaymentForEmptyOrderWithoutPersistedChanges() throws Exception {
        UUID orderId = persistOrder();
        transactionTemplate.executeWithoutResult(status -> {
            var order = orderRepository.findById(orderId).orElseThrow();
            var item = order.getItems().get(0);
            order.removeItem(item);
            orderRepository.saveAndFlush(order);
        });
        var initialVersion = orderRepository.findById(orderId).orElseThrow().getVersion();
        String key = UUID.randomUUID().toString();
        var request = new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, key);

        mockMvc.perform(post(BASE_PATH, orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message")
                        .value("Order must contain items and have a positive total to start payment."));

        transactionTemplate.executeWithoutResult(status -> {
            var order = orderRepository.findById(orderId).orElseThrow();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(order.getVersion()).isEqualTo(initialVersion);
            assertThat(order.getItems()).isEmpty();
            assertThat(order.getAmountTotal()).isEqualByComparingTo("0");
            assertThat(paymentRepository.findByOrderId(orderId)).isEmpty();
            assertThat(paymentRepository.findByIdempotencyKey(key)).isEmpty();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = { "0.00", "-1.00" })
    void shouldRejectPaymentForNonPositiveOrderTotalWithoutPersistedChanges(String total) throws Exception {
        UUID orderId = persistOrder();
        // Simulate inconsistent persisted data while retaining the order item.
        transactionTemplate.executeWithoutResult(status -> entityManager
                .createNativeQuery("UPDATE orders SET amount_total = :total WHERE id = :id")
                .setParameter("total", new BigDecimal(total)).setParameter("id", orderId).executeUpdate());
        var initialVersion = orderRepository.findById(orderId).orElseThrow().getVersion();
        String key = UUID.randomUUID().toString();
        var request = new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, key);

        mockMvc.perform(post(BASE_PATH, orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message")
                        .value("Order must contain items and have a positive total to start payment."));

        transactionTemplate.executeWithoutResult(status -> {
            var order = orderRepository.findById(orderId).orElseThrow();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(order.getVersion()).isEqualTo(initialVersion);
            assertThat(order.getItems()).hasSize(1);
            assertThat(order.getAmountTotal()).isEqualByComparingTo(total);
            assertThat(paymentRepository.findByOrderId(orderId)).isEmpty();
            assertThat(paymentRepository.findByIdempotencyKey(key)).isEmpty();
        });
    }

    private UUID persistOrder() {
        return transactionTemplate.execute(status -> {
            productRepository.findBySku("SKU-123").orElseGet(() -> {
                ProductEntity persistedProduct = productRepository.saveAndFlush(ProductEntity.builder()
                        .sku("SKU-123").name("Payment product").description("Payment fixture")
                        .price(new BigDecimal("123.50")).quantity(100L).build());
                reservationProductIds.add(persistedProduct.getId());
                return persistedProduct;
            });
            UUID customerId = customerRepository.saveAndFlush(
                    new CustomerEntity(UUID.randomUUID() + "@example.com", "Test Customer")).getId();
            customerIds.add(customerId);
            UUID orderId = orderRepository.saveAndFlush(OrderEntity.builder()
                    .customerId(customerId)
                    .addItem(OrderItemEntity.builder().sku("SKU-123").description("Test item")
                            .unitPrice(new BigDecimal("123.50")).quantity(2L).build())
                    .build()).getId();
            orderIds.add(orderId);
            return orderId;
        });
    }

    private UUID persistPayment(UUID orderId, PaymentStatus paymentStatus) {
        return transactionTemplate.execute(status -> {
            UUID id = paymentRepository.saveAndFlush(PaymentAttemptEntity.builder()
                    .orderId(orderId).amount(new BigDecimal("247.00"))
                    .provider(PaymentProvider.STRIPE).status(paymentStatus).build()).getId();
            paymentIds.add(id);
            return id;
        });
    }
}
