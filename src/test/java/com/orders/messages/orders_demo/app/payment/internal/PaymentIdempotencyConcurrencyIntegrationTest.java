package com.orders.messages.orders_demo.app.payment.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.order.internal.OrderEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderItemEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderRepository;
import com.orders.messages.orders_demo.app.order.internal.OrderStatus;
import com.orders.messages.orders_demo.app.payment.api.CreatePaymentAttemptRequest;
import com.orders.messages.orders_demo.app.payment.api.PaymentAttemptResponse;
import com.orders.messages.orders_demo.app.payment.api.PaymentFailedRequest;
import com.orders.messages.orders_demo.app.payment.api.PaymentSucceededRequest;
import com.orders.messages.orders_demo.app.product.internal.ProductEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductRepository;

@AutoConfigureMockMvc
class PaymentIdempotencyConcurrencyIntegrationTest extends PostgresContainer {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PaymentAttemptService paymentService;
    @MockitoSpyBean
    private PaymentAttemptRepository paymentRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<UUID> orderIds = new ArrayList<>();
    private final List<UUID> customerIds = new ArrayList<>();
    private final List<UUID> productIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            orderIds.forEach(orderId -> paymentRepository.deleteAll(paymentRepository.findByOrderId(orderId)));
            paymentRepository.flush();
            orderIds.forEach(orderRepository::deleteById);
            orderRepository.flush();
            customerIds.forEach(customerRepository::deleteById);
            customerRepository.flush();
            productIds.forEach(productRepository::deleteById);
            productRepository.flush();
        });
    }

    @ParameterizedTest
    @ValueSource(longs = { 2, 5 })
    void shouldReturnSamePaymentForConcurrentCreationRequestsWithSameIdempotencyKeyAndReserveStockOnce(long initialStock)
            throws Exception {
        UUID productId = persistProduct(initialStock);
        UUID orderId = persistOrder(productId);
        Long initialOrderVersion = orderRepository.findById(orderId).orElseThrow().getVersion();
        var createPaymentRequest = createPaymentRequest();
        synchronizeInitialIdempotencyReads(createPaymentRequest.idempotencyKey());

        var creationHttpResults = performConcurrently(
                () -> createPaymentThroughHttp(orderId, createPaymentRequest),
                () -> createPaymentThroughHttp(orderId, createPaymentRequest));

        assertThat(creationHttpResults).as("Both identical creation requests must return a successful response")
                .allSatisfy(result -> assertThat(result.getResponse().getStatus()).isIn(200, 201));
        var creationResponses = readPaymentResponses(creationHttpResults);
        var persistedPayments = paymentRepository.findByOrderId(orderId);
        assertThat(persistedPayments).hasSize(1);
        var createdPayment = persistedPayments.get(0);
        assertThat(creationResponses).extracting(PaymentAttemptResponse::id).containsOnly(createdPayment.getId());
        assertThat(creationResponses).extracting(PaymentAttemptResponse::orderId).containsOnly(orderId);
        assertThat(creationResponses).extracting(PaymentAttemptResponse::status).containsOnly(PaymentStatus.CREATED);
        assertThat(creationResponses).extracting(PaymentAttemptResponse::provider).containsOnly(PaymentProvider.STRIPE);
        assertThat(creationResponses).extracting(PaymentAttemptResponse::idempotencyKey)
                .containsOnly(createPaymentRequest.idempotencyKey());
        assertThat(createdPayment.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(createdPayment.getIdempotencyKey()).isEqualTo(createPaymentRequest.idempotencyKey());
        assertThat(createdPayment.getAmount()).isEqualByComparingTo("200.00");
        assertThat(createdPayment.getVersion()).isZero();
        assertThat(paymentRepository.findByIdempotencyKey(createPaymentRequest.idempotencyKey()).orElseThrow().getId())
                .isEqualTo(createdPayment.getId());
        var processingOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(processingOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
        assertThat(processingOrder.getVersion()).isEqualTo(initialOrderVersion + 1);
        assertThat(processingOrder.getAmountTotal()).isEqualByComparingTo("200.00");
        assertThat(productRepository.findById(productId).orElseThrow().getQuantity()).isEqualTo(initialStock - 2);
    }

    @Test
    void shouldRejectConcurrentKeyReuseForAnotherOrderWithoutReturningOrReservingItsPayment() throws Exception {
        UUID productId = persistProduct();
        UUID firstOrderId = persistOrder(productId);
        UUID secondOrderId = persistOrder(productId);
        var createPaymentRequest = createPaymentRequest();
        synchronizeInitialIdempotencyReads(createPaymentRequest.idempotencyKey());

        var creationHttpResults = performConcurrently(
                () -> createPaymentThroughHttp(firstOrderId, createPaymentRequest),
                () -> createPaymentThroughHttp(secondOrderId, createPaymentRequest));

        assertThat(creationHttpResults).extracting(result -> result.getResponse().getStatus())
                .containsExactlyInAnyOrder(201, 409);
        var successfulResult = creationHttpResults.stream()
                .filter(result -> result.getResponse().getStatus() == 201).findFirst().orElseThrow();
        var conflictResult = creationHttpResults.stream()
                .filter(result -> result.getResponse().getStatus() == 409).findFirst().orElseThrow();
        var createdPaymentResponse = objectMapper.readValue(successfulResult.getResponse().getContentAsString(),
                PaymentAttemptResponse.class);
        var conflictJson = objectMapper.readTree(conflictResult.getResponse().getContentAsString());
        assertThat(conflictJson.path("message").asText()).isEqualTo("Requested order ID is not equal to payment's order.");
        var persistedPayments = new ArrayList<>(paymentRepository.findByOrderId(firstOrderId));
        persistedPayments.addAll(paymentRepository.findByOrderId(secondOrderId));
        assertThat(persistedPayments).hasSize(1);
        var createdPayment = persistedPayments.get(0);
        assertThat(createdPaymentResponse.id()).isEqualTo(createdPayment.getId());
        assertThat(createdPaymentResponse.orderId()).isEqualTo(createdPayment.getOrderId());
        assertThat(createdPayment.getIdempotencyKey()).isEqualTo(createPaymentRequest.idempotencyKey());
        assertThat(createdPayment.getStatus()).isEqualTo(PaymentStatus.CREATED);
        var persistedOrders = orderRepository.findAllById(List.of(firstOrderId, secondOrderId));
        assertThat(persistedOrders).extracting(OrderEntity::getStatus)
                .containsExactlyInAnyOrder(OrderStatus.PAYMENT_IN_PROGRESS, OrderStatus.PENDING_PAYMENT);
        var processingOrder = persistedOrders.stream()
                .filter(order -> order.getStatus() == OrderStatus.PAYMENT_IN_PROGRESS).findFirst().orElseThrow();
        var rejectedOrder = persistedOrders.stream()
                .filter(order -> order.getStatus() == OrderStatus.PENDING_PAYMENT).findFirst().orElseThrow();
        assertThat(processingOrder.getId()).isEqualTo(createdPayment.getOrderId());
        assertThat(processingOrder.getVersion()).isEqualTo(1L);
        assertThat(rejectedOrder.getVersion()).isZero();
        assertThat(paymentRepository.findByOrderId(rejectedOrder.getId())).isEmpty();
        assertThat(productRepository.findById(productId).orElseThrow().getQuantity()).isEqualTo(3L);
    }

    @Test
    void shouldAcceptConcurrentIdenticalSuccessNotificationsAndMarkOrderPaidOnce() throws Exception {
        UUID productId = persistProduct();
        UUID orderId = persistOrder(productId);
        UUID paymentId = createProcessingPayment(orderId);
        Long initialPaymentVersion = paymentRepository.findById(paymentId).orElseThrow().getVersion();
        Long initialOrderVersion = orderRepository.findById(orderId).orElseThrow().getVersion();
        var paymentSucceededRequest = new PaymentSucceededRequest("identical-success-ref");
        synchronizeInitialPaymentReads(paymentId);

        var successHttpResults = performConcurrently(
                () -> notifySuccessThroughHttp(orderId, paymentId, paymentSucceededRequest),
                () -> notifySuccessThroughHttp(orderId, paymentId, paymentSucceededRequest));

        assertThat(successHttpResults).as("Both identical success notifications must be recognized")
                .extracting(result -> result.getResponse().getStatus()).containsOnly(200);
        var successResponses = readPaymentResponses(successHttpResults);
        assertThat(successResponses).extracting(PaymentAttemptResponse::id).containsOnly(paymentId);
        assertThat(successResponses).extracting(PaymentAttemptResponse::status).containsOnly(PaymentStatus.SUCCEEDED);
        assertThat(successResponses).extracting(PaymentAttemptResponse::providerRef)
                .containsOnly(paymentSucceededRequest.providerRef());
        var succeededPayment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(succeededPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(succeededPayment.getProviderRef()).isEqualTo(paymentSucceededRequest.providerRef());
        assertThat(succeededPayment.getVersion()).isEqualTo(initialPaymentVersion + 1);
        assertThat(succeededPayment.getAmount()).isEqualByComparingTo("200.00");
        var paidOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paidOrder.getVersion()).isEqualTo(initialOrderVersion + 1);
        assertThat(paidOrder.getAmountTotal()).isEqualByComparingTo("200.00");
        assertThat(productRepository.findById(productId).orElseThrow().getQuantity()).isEqualTo(3L);
        assertThat(paymentRepository.findByOrderId(orderId)).hasSize(1);
    }

    @Test
    void shouldAcceptConcurrentIdenticalFailureNotificationsAndRestoreStockOnce() throws Exception {
        UUID productId = persistProduct();
        UUID orderId = persistOrder(productId);
        UUID paymentId = createProcessingPayment(orderId);
        Long initialPaymentVersion = paymentRepository.findById(paymentId).orElseThrow().getVersion();
        Long initialOrderVersion = orderRepository.findById(orderId).orElseThrow().getVersion();
        var paymentFailedRequest = new PaymentFailedRequest(402, "Declined");
        synchronizeInitialPaymentReads(paymentId);

        var failureHttpResults = performConcurrently(
                () -> notifyFailureThroughHttp(orderId, paymentId, paymentFailedRequest),
                () -> notifyFailureThroughHttp(orderId, paymentId, paymentFailedRequest));

        assertThat(failureHttpResults).as("Both identical failure notifications must be recognized")
                .extracting(result -> result.getResponse().getStatus()).containsOnly(200);
        var failureResponses = readPaymentResponses(failureHttpResults);
        assertThat(failureResponses).extracting(PaymentAttemptResponse::id).containsOnly(paymentId);
        assertThat(failureResponses).extracting(PaymentAttemptResponse::status).containsOnly(PaymentStatus.FAILED);
        assertThat(failureResponses).extracting(PaymentAttemptResponse::failureCode).containsOnly(paymentFailedRequest.code());
        assertThat(failureResponses).extracting(PaymentAttemptResponse::failureMessage)
                .containsOnly(paymentFailedRequest.errorMessage());
        var failedPayment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(failedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(failedPayment.getFailureCode()).isEqualTo(paymentFailedRequest.code());
        assertThat(failedPayment.getFailureMessage()).isEqualTo(paymentFailedRequest.errorMessage());
        assertThat(failedPayment.getVersion()).isEqualTo(initialPaymentVersion + 1);
        assertThat(failedPayment.getAmount()).isEqualByComparingTo("200.00");
        var pendingOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(pendingOrder.getVersion()).isEqualTo(initialOrderVersion + 1);
        assertThat(pendingOrder.getAmountTotal()).isEqualByComparingTo("200.00");
        assertThat(productRepository.findById(productId).orElseThrow().getQuantity()).isEqualTo(5L);
        assertThat(paymentRepository.findByOrderId(orderId)).hasSize(1);
    }

    @Test
    void shouldRejectConcurrentSuccessWithDifferentReferencesWithoutOverwritingCommittedResult() throws Exception {
        UUID productId = persistProduct();
        UUID orderId = persistOrder(productId);
        UUID paymentId = createProcessingPayment(orderId);
        Long initialPaymentVersion = paymentRepository.findById(paymentId).orElseThrow().getVersion();
        Long initialOrderVersion = orderRepository.findById(orderId).orElseThrow().getVersion();
        var firstSuccessRequest = new PaymentSucceededRequest("first-success-ref");
        var secondSuccessRequest = new PaymentSucceededRequest("second-success-ref");
        synchronizeInitialPaymentReads(paymentId);

        var successHttpResults = performConcurrently(
                () -> notifySuccessThroughHttp(orderId, paymentId, firstSuccessRequest),
                () -> notifySuccessThroughHttp(orderId, paymentId, secondSuccessRequest));

        assertThat(successHttpResults).extracting(result -> result.getResponse().getStatus())
                .containsExactlyInAnyOrder(200, 409);
        var successfulHttpResult = successHttpResults.stream()
                .filter(result -> result.getResponse().getStatus() == 200).findFirst().orElseThrow();
        var succeededPaymentResponse = objectMapper.readValue(successfulHttpResult.getResponse().getContentAsString(),
                PaymentAttemptResponse.class);
        var succeededPayment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(succeededPaymentResponse.id()).isEqualTo(paymentId);
        assertThat(succeededPaymentResponse.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(succeededPayment.getProviderRef()).isIn(firstSuccessRequest.providerRef(), secondSuccessRequest.providerRef());
        assertThat(succeededPayment.getProviderRef()).isEqualTo(succeededPaymentResponse.providerRef());
        assertThat(succeededPayment.getVersion()).isEqualTo(initialPaymentVersion + 1);
        var paidOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paidOrder.getVersion()).isEqualTo(initialOrderVersion + 1);
        assertThat(productRepository.findById(productId).orElseThrow().getQuantity()).isEqualTo(3L);
        assertThat(paymentRepository.findByOrderId(orderId)).hasSize(1);
    }

    @Test
    void shouldRejectConcurrentFailureWithDifferentDetailsWithoutRestoringStockTwice() throws Exception {
        UUID productId = persistProduct();
        UUID orderId = persistOrder(productId);
        UUID paymentId = createProcessingPayment(orderId);
        Long initialPaymentVersion = paymentRepository.findById(paymentId).orElseThrow().getVersion();
        Long initialOrderVersion = orderRepository.findById(orderId).orElseThrow().getVersion();
        var firstFailureRequest = new PaymentFailedRequest(402, "Declined");
        var secondFailureRequest = new PaymentFailedRequest(500, "Server error");
        var expectedFailureMessages = Map.of(402, "Declined", 500, "Server error");
        synchronizeInitialPaymentReads(paymentId);

        var failureHttpResults = performConcurrently(
                () -> notifyFailureThroughHttp(orderId, paymentId, firstFailureRequest),
                () -> notifyFailureThroughHttp(orderId, paymentId, secondFailureRequest));

        assertThat(failureHttpResults).extracting(result -> result.getResponse().getStatus())
                .containsExactlyInAnyOrder(200, 409);
        var successfulHttpResult = failureHttpResults.stream()
                .filter(result -> result.getResponse().getStatus() == 200).findFirst().orElseThrow();
        var failedPaymentResponse = objectMapper.readValue(successfulHttpResult.getResponse().getContentAsString(),
                PaymentAttemptResponse.class);
        var failedPayment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(failedPaymentResponse.id()).isEqualTo(paymentId);
        assertThat(failedPaymentResponse.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(failedPayment.getFailureCode()).isIn(402, 500);
        assertThat(failedPayment.getFailureCode()).isEqualTo(failedPaymentResponse.failureCode());
        assertThat(failedPayment.getFailureMessage()).isEqualTo(failedPaymentResponse.failureMessage());
        assertThat(failedPayment.getFailureMessage()).isEqualTo(expectedFailureMessages.get(failedPayment.getFailureCode()));
        assertThat(failedPayment.getVersion()).isEqualTo(initialPaymentVersion + 1);
        var pendingOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(pendingOrder.getVersion()).isEqualTo(initialOrderVersion + 1);
        assertThat(productRepository.findById(productId).orElseThrow().getQuantity()).isEqualTo(5L);
        assertThat(paymentRepository.findByOrderId(orderId)).hasSize(1);
    }

    private void synchronizeInitialIdempotencyReads(String idempotencyKey) {
        var initialReads = new CountDownLatch(2);
        var originalRepositoryAnswer = mockingDetails(paymentRepository).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            var paymentSnapshot = originalRepositoryAnswer.answer(invocation);
            awaitInitialReads(initialReads);
            return paymentSnapshot;
        }).when(paymentRepository).findByIdempotencyKey(idempotencyKey);
    }

    private void synchronizeInitialPaymentReads(UUID paymentId) {
        var initialReads = new CountDownLatch(2);
        var originalRepositoryAnswer = mockingDetails(paymentRepository).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            var paymentSnapshot = originalRepositoryAnswer.answer(invocation);
            awaitInitialReads(initialReads);
            return paymentSnapshot;
        }).when(paymentRepository).findById(paymentId);
    }

    private void awaitInitialReads(CountDownLatch initialReads) {
        // Tras las primeras dos lecturas, los reintentos pueden volver a consultar sin esperar.
        initialReads.countDown();
        try {
            assertThat(initialReads.await(10, TimeUnit.SECONDS)).as("Both requests must read before either continues")
                    .isTrue();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent payment request interrupted", interruptedException);
        }
    }

    private List<MvcResult> performConcurrently(Callable<MvcResult> firstRequest, Callable<MvcResult> secondRequest)
            throws Exception {
        var paymentExecutor = Executors.newFixedThreadPool(2);
        try {
            var firstResponseFuture = paymentExecutor.submit(firstRequest);
            var secondResponseFuture = paymentExecutor.submit(secondRequest);
            return List.of(firstResponseFuture.get(20, TimeUnit.SECONDS), secondResponseFuture.get(20, TimeUnit.SECONDS));
        } finally {
            paymentExecutor.shutdownNow();
            assertThat(paymentExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private List<PaymentAttemptResponse> readPaymentResponses(List<MvcResult> httpResults) throws Exception {
        var firstPaymentResponse = objectMapper.readValue(httpResults.get(0).getResponse().getContentAsString(),
                PaymentAttemptResponse.class);
        var secondPaymentResponse = objectMapper.readValue(httpResults.get(1).getResponse().getContentAsString(),
                PaymentAttemptResponse.class);
        return List.of(firstPaymentResponse, secondPaymentResponse);
    }

    private MvcResult createPaymentThroughHttp(UUID orderId, CreatePaymentAttemptRequest createPaymentRequest) throws Exception {
        return mockMvc.perform(post(paymentPath(orderId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createPaymentRequest))).andReturn();
    }

    private MvcResult notifySuccessThroughHttp(UUID orderId, UUID paymentId, PaymentSucceededRequest paymentSucceededRequest)
            throws Exception {
        return mockMvc.perform(patch(paymentPath(orderId) + "/" + paymentId + "/succeeded")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentSucceededRequest))).andReturn();
    }

    private MvcResult notifyFailureThroughHttp(UUID orderId, UUID paymentId, PaymentFailedRequest paymentFailedRequest)
            throws Exception {
        return mockMvc.perform(patch(paymentPath(orderId) + "/" + paymentId + "/failed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentFailedRequest))).andReturn();
    }

    private String paymentPath(UUID orderId) {
        return "/api/v1/orders/" + orderId + "/payments";
    }

    private UUID createProcessingPayment(UUID orderId) {
        var createdPaymentResponse = paymentService.createPaymentAttempt(orderId, createPaymentRequest());
        paymentService.startProcessing(orderId, createdPaymentResponse.id());
        return createdPaymentResponse.id();
    }

    private CreatePaymentAttemptRequest createPaymentRequest() {
        return new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, UUID.randomUUID().toString());
    }

    private UUID persistProduct() {
        return persistProduct(5L);
    }

    private UUID persistProduct(long initialStock) {
        var savedProduct = productRepository.saveAndFlush(ProductEntity.builder()
                .sku(UUID.randomUUID().toString()).name("Product").price(new BigDecimal("100.00"))
                .quantity(initialStock).build());
        productIds.add(savedProduct.getId());
        return savedProduct.getId();
    }

    private UUID persistOrder(UUID productId) {
        return transactionTemplate.execute(status -> {
            var savedCustomer = customerRepository.saveAndFlush(
                    new CustomerEntity(UUID.randomUUID() + "@example.com", "Customer"));
            customerIds.add(savedCustomer.getId());
            var product = productRepository.findById(productId).orElseThrow();
            var orderItem = OrderItemEntity.builder().sku(product.getSku())
                    .unitPrice(new BigDecimal("100.00")).quantity(2L).build();
            var savedOrder = orderRepository.saveAndFlush(OrderEntity.builder()
                    .customerId(savedCustomer.getId()).addItem(orderItem).build());
            orderIds.add(savedOrder.getId());
            return savedOrder.getId();
        });
    }
}
