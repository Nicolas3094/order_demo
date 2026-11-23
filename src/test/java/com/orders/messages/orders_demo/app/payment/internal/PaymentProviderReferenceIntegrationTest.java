package com.orders.messages.orders_demo.app.payment.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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
import com.orders.messages.orders_demo.app.payment.api.PaymentSucceededRequest;
import com.orders.messages.orders_demo.app.payment.internal.exceptions.InvalidPaymentStateException;
import com.orders.messages.orders_demo.app.product.internal.ProductEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductRepository;

@AutoConfigureMockMvc
class PaymentProviderReferenceIntegrationTest extends PostgresContainer {
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

    @Test
    void shouldRejectReferenceFromAnotherPaymentOfSameProviderThroughHttpWith409WithoutChangingPaymentsOrdersOrStock()
            throws Exception {
        UUID productId = persistProduct();
        UUID firstOrderId = persistOrder(productId);
        UUID secondOrderId = persistOrder(productId);
        UUID firstPaymentId = createProcessingPayment(firstOrderId, PaymentProvider.STRIPE);
        UUID secondPaymentId = createProcessingPayment(secondOrderId, PaymentProvider.STRIPE);
        String providerReference = "shared-provider-ref";
        paymentService.markAsSucceeded(firstOrderId, firstPaymentId, providerReference);
        var firstPaymentBeforeRejection = paymentRepository.findById(firstPaymentId).orElseThrow();
        var secondPaymentBeforeRejection = paymentRepository.findById(secondPaymentId).orElseThrow();
        Long firstOrderVersion = orderRepository.findById(firstOrderId).orElseThrow().getVersion();
        Long secondOrderVersion = orderRepository.findById(secondOrderId).orElseThrow().getVersion();

        notifySuccessfulPayment(secondOrderId, secondPaymentId, providerReference)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("A successful payment has the same provider reference."))
                .andExpect(jsonPath("$.path").value(paymentSuccessPath(secondOrderId, secondPaymentId)));

        var firstPaymentAfterRejection = paymentRepository.findById(firstPaymentId).orElseThrow();
        assertThat(firstPaymentAfterRejection.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(firstPaymentAfterRejection.getProviderRef()).isEqualTo(providerReference);
        assertThat(firstPaymentAfterRejection.getVersion()).isEqualTo(firstPaymentBeforeRejection.getVersion());
        assertThat(firstPaymentAfterRejection.getUpdatedAt()).isEqualTo(firstPaymentBeforeRejection.getUpdatedAt());
        var secondPaymentAfterRejection = paymentRepository.findById(secondPaymentId).orElseThrow();
        assertThat(secondPaymentAfterRejection.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(secondPaymentAfterRejection.getProviderRef()).isNull();
        assertThat(secondPaymentAfterRejection.getVersion()).isEqualTo(secondPaymentBeforeRejection.getVersion());
        assertThat(secondPaymentAfterRejection.getUpdatedAt()).isEqualTo(secondPaymentBeforeRejection.getUpdatedAt());
        var firstOrder = orderRepository.findById(firstOrderId).orElseThrow();
        assertThat(firstOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(firstOrder.getVersion()).isEqualTo(firstOrderVersion);
        var secondOrder = orderRepository.findById(secondOrderId).orElseThrow();
        assertThat(secondOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
        assertThat(secondOrder.getVersion()).isEqualTo(secondOrderVersion);
        assertThat(productRepository.findById(productId).orElseThrow().getQuantity()).isEqualTo(6L);
        assertThat(paymentRepository.findByOrderId(firstOrderId)).hasSize(1);
        assertThat(paymentRepository.findByOrderId(secondOrderId)).hasSize(1);
    }

    @Test
    void shouldAllowSameReferenceForDifferentProvidersThroughHttpAndKeepBothOrdersPaid() throws Exception {
        UUID productId = persistProduct();
        UUID stripeOrderId = persistOrder(productId);
        UUID paypalOrderId = persistOrder(productId);
        UUID stripePaymentId = createProcessingPayment(stripeOrderId, PaymentProvider.STRIPE);
        UUID paypalPaymentId = createProcessingPayment(paypalOrderId, PaymentProvider.PAYPAL);
        String providerReference = "shared-provider-ref";

        notifySuccessfulPayment(stripeOrderId, stripePaymentId, providerReference)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(stripePaymentId.toString()))
                .andExpect(jsonPath("$.provider").value("STRIPE"))
                .andExpect(jsonPath("$.providerRef").value(providerReference))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        notifySuccessfulPayment(paypalOrderId, paypalPaymentId, providerReference)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paypalPaymentId.toString()))
                .andExpect(jsonPath("$.provider").value("PAYPAL"))
                .andExpect(jsonPath("$.providerRef").value(providerReference))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        var succeededPayments = paymentRepository.findAllById(List.of(stripePaymentId, paypalPaymentId));
        assertThat(succeededPayments).hasSize(2);
        assertThat(succeededPayments).extracting(PaymentAttemptEntity::getProvider)
                .containsExactlyInAnyOrder(PaymentProvider.STRIPE, PaymentProvider.PAYPAL);
        assertThat(succeededPayments).extracting(PaymentAttemptEntity::getProviderRef).containsOnly(providerReference);
        assertThat(succeededPayments).extracting(PaymentAttemptEntity::getStatus).containsOnly(PaymentStatus.SUCCEEDED);
        assertThat(orderRepository.findAllById(List.of(stripeOrderId, paypalOrderId)))
                .extracting(OrderEntity::getStatus).containsOnly(OrderStatus.PAID);
        assertThat(productRepository.findById(productId).orElseThrow().getQuantity()).isEqualTo(6L);
    }

    @Test
    void shouldAcceptRepeatedReferenceForSamePaymentThroughHttpWithoutChangingVersionsOrStock() throws Exception {
        UUID productId = persistProduct();
        UUID orderId = persistOrder(productId);
        UUID paymentId = createProcessingPayment(orderId, PaymentProvider.STRIPE);
        String providerReference = "same-payment-ref";
        paymentService.markAsSucceeded(orderId, paymentId, providerReference);
        var paymentBeforeRepetition = paymentRepository.findById(paymentId).orElseThrow();
        Long orderVersionBeforeRepetition = orderRepository.findById(orderId).orElseThrow().getVersion();

        notifySuccessfulPayment(orderId, paymentId, providerReference)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.providerRef").value(providerReference));

        var paymentAfterRepetition = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(paymentAfterRepetition.getVersion()).isEqualTo(paymentBeforeRepetition.getVersion());
        assertThat(paymentAfterRepetition.getUpdatedAt()).isEqualTo(paymentBeforeRepetition.getUpdatedAt());
        var paidOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paidOrder.getVersion()).isEqualTo(orderVersionBeforeRepetition);
        assertThat(productRepository.findById(productId).orElseThrow().getQuantity()).isEqualTo(8L);
        assertThat(paymentRepository.findByOrderId(orderId)).hasSize(1);
    }

    @Test
    void shouldAllowOnlyOnePaymentToSucceedWhenSameProviderReferenceIsAssignedConcurrently() throws Exception {
        UUID productId = persistProduct();
        UUID firstOrderId = persistOrder(productId);
        UUID secondOrderId = persistOrder(productId);
        UUID firstPaymentId = createProcessingPayment(firstOrderId, PaymentProvider.STRIPE);
        UUID secondPaymentId = createProcessingPayment(secondOrderId, PaymentProvider.STRIPE);
        String providerReference = "concurrent-provider-ref";
        var paymentReadBarrier = new CyclicBarrier(2);
        var paymentExecutor = Executors.newFixedThreadPool(2);

        try {
            var firstSuccessFuture = paymentExecutor.submit(
                    () -> tryCompletingPayment(firstOrderId, firstPaymentId, providerReference, paymentReadBarrier));
            var secondSuccessFuture = paymentExecutor.submit(
                    () -> tryCompletingPayment(secondOrderId, secondPaymentId, providerReference, paymentReadBarrier));

            boolean firstPaymentSucceeded = firstSuccessFuture.get(20, TimeUnit.SECONDS);
            boolean secondPaymentSucceeded = secondSuccessFuture.get(20, TimeUnit.SECONDS);

            assertThat(List.of(firstPaymentSucceeded, secondPaymentSucceeded)).containsExactlyInAnyOrder(true, false);
            var persistedPayments = paymentRepository.findAllById(List.of(firstPaymentId, secondPaymentId));
            assertThat(persistedPayments).hasSize(2);
            assertThat(persistedPayments).extracting(PaymentAttemptEntity::getStatus)
                    .containsExactlyInAnyOrder(PaymentStatus.SUCCEEDED, PaymentStatus.PROCESSING);
            assertThat(persistedPayments).extracting(PaymentAttemptEntity::getProviderRef)
                    .containsExactlyInAnyOrder(providerReference, null);
            var succeededPayment = persistedPayments.stream()
                    .filter(payment -> payment.getStatus() == PaymentStatus.SUCCEEDED).findFirst().orElseThrow();
            var rejectedPayment = persistedPayments.stream()
                    .filter(payment -> payment.getStatus() == PaymentStatus.PROCESSING).findFirst().orElseThrow();
            assertThat(orderRepository.findById(succeededPayment.getOrderId()).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PAID);
            assertThat(orderRepository.findById(rejectedPayment.getOrderId()).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
            assertThat(productRepository.findById(productId).orElseThrow().getQuantity()).isEqualTo(6L);
        } finally {
            paymentExecutor.shutdownNow();
            assertThat(paymentExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void shouldReturn200And409WhenConcurrentHttpSuccessRequestsPassReferenceCheckWithoutChangingRejectedPayment()
            throws Exception {
        UUID productId = persistProduct();
        UUID firstOrderId = persistOrder(productId);
        UUID secondOrderId = persistOrder(productId);
        UUID firstPaymentId = createProcessingPayment(firstOrderId, PaymentProvider.STRIPE);
        UUID secondPaymentId = createProcessingPayment(secondOrderId, PaymentProvider.STRIPE);
        String providerReference = "concurrent-http-provider-ref";
        var firstPaymentBeforeRequests = paymentRepository.findById(firstPaymentId).orElseThrow();
        var secondPaymentBeforeRequests = paymentRepository.findById(secondPaymentId).orElseThrow();
        var initialPaymentVersions = Map.of(firstPaymentId, firstPaymentBeforeRequests.getVersion(),
                secondPaymentId, secondPaymentBeforeRequests.getVersion());
        var initialPaymentTimestamps = Map.of(firstPaymentId, firstPaymentBeforeRequests.getUpdatedAt(),
                secondPaymentId, secondPaymentBeforeRequests.getUpdatedAt());
        var initialOrderVersions = Map.of(firstOrderId, orderRepository.findById(firstOrderId).orElseThrow().getVersion(),
                secondOrderId, orderRepository.findById(secondOrderId).orElseThrow().getVersion());
        var referenceCheckBarrier = new CyclicBarrier(2);
        var paymentExecutor = Executors.newFixedThreadPool(2);

        // El repositorio es un proxy: conservamos su delegación a la consulta original.
        var originalRepositoryAnswer = mockingDetails(paymentRepository).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            boolean referenceExists = (boolean) originalRepositoryAnswer.answer(invocation);
            assertThat(referenceExists).isFalse();
            awaitPaymentReadBarrier(referenceCheckBarrier);
            return referenceExists;
        }).when(paymentRepository).existsByProviderAndProviderRef(PaymentProvider.STRIPE, providerReference);

        try {
            // Cada petición conserva su propia transacción HTTP: el commit ocurre antes de responder.
            var firstSuccessFuture = paymentExecutor.submit(
                    () -> notifySuccessfulPayment(firstOrderId, firstPaymentId, providerReference).andReturn());
            var secondSuccessFuture = paymentExecutor.submit(
                    () -> notifySuccessfulPayment(secondOrderId, secondPaymentId, providerReference).andReturn());
            MvcResult firstHttpResult = firstSuccessFuture.get(20, TimeUnit.SECONDS);
            MvcResult secondHttpResult = secondSuccessFuture.get(20, TimeUnit.SECONDS);
            var httpResults = List.of(firstHttpResult, secondHttpResult);

            assertThat(httpResults)
                    .as("Concurrent HTTP results; first exception: %s; second exception: %s",
                            firstHttpResult.getResolvedException(), secondHttpResult.getResolvedException())
                    .extracting(result -> result.getResponse().getStatus())
                    .containsExactlyInAnyOrder(200, 409);
            var successfulHttpResult = httpResults.stream()
                    .filter(result -> result.getResponse().getStatus() == 200).findFirst().orElseThrow();
            var conflictHttpResult = httpResults.stream()
                    .filter(result -> result.getResponse().getStatus() == 409).findFirst().orElseThrow();
            var successJson = objectMapper.readTree(successfulHttpResult.getResponse().getContentAsString());
            var conflictJson = objectMapper.readTree(conflictHttpResult.getResponse().getContentAsString());
            assertThat(successJson.path("status").asText()).isEqualTo("SUCCEEDED");
            assertThat(successJson.path("providerRef").asText()).isEqualTo(providerReference);
            assertThat(conflictHttpResult.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
            assertThat(conflictJson.path("status").asInt()).isEqualTo(409);
            assertThat(conflictJson.path("message").asText())
                    .isEqualTo("A successful payment has the same provider reference.");

            var persistedPayments = paymentRepository.findAllById(List.of(firstPaymentId, secondPaymentId));
            assertThat(persistedPayments).hasSize(2);
            assertThat(persistedPayments).extracting(PaymentAttemptEntity::getStatus)
                    .containsExactlyInAnyOrder(PaymentStatus.SUCCEEDED, PaymentStatus.PROCESSING);
            var succeededPayment = persistedPayments.stream()
                    .filter(payment -> payment.getStatus() == PaymentStatus.SUCCEEDED).findFirst().orElseThrow();
            var rejectedPayment = persistedPayments.stream()
                    .filter(payment -> payment.getStatus() == PaymentStatus.PROCESSING).findFirst().orElseThrow();
            assertThat(successJson.path("id").asText()).isEqualTo(succeededPayment.getId().toString());
            assertThat(succeededPayment.getProviderRef()).isEqualTo(providerReference);
            assertThat(succeededPayment.getVersion()).isEqualTo(initialPaymentVersions.get(succeededPayment.getId()) + 1);
            assertThat(rejectedPayment.getProviderRef()).isNull();
            assertThat(rejectedPayment.getVersion()).isEqualTo(initialPaymentVersions.get(rejectedPayment.getId()));
            assertThat(rejectedPayment.getUpdatedAt()).isEqualTo(initialPaymentTimestamps.get(rejectedPayment.getId()));
            assertThat(conflictJson.path("path").asText())
                    .isEqualTo(paymentSuccessPath(rejectedPayment.getOrderId(), rejectedPayment.getId()));
            var paidOrder = orderRepository.findById(succeededPayment.getOrderId()).orElseThrow();
            assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(paidOrder.getVersion()).isEqualTo(initialOrderVersions.get(paidOrder.getId()) + 1);
            var processingOrder = orderRepository.findById(rejectedPayment.getOrderId()).orElseThrow();
            assertThat(processingOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
            assertThat(processingOrder.getVersion()).isEqualTo(initialOrderVersions.get(processingOrder.getId()));
            assertThat(productRepository.findById(productId).orElseThrow().getQuantity()).isEqualTo(6L);
        } finally {
            paymentExecutor.shutdownNow();
            assertThat(paymentExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private boolean tryCompletingPayment(UUID orderId, UUID paymentId, String providerReference,
            CyclicBarrier paymentReadBarrier) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                var processingPayment = paymentRepository.findById(paymentId).orElseThrow();
                assertThat(processingPayment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
                awaitPaymentReadBarrier(paymentReadBarrier);
                paymentService.markAsSucceeded(orderId, paymentId, providerReference);
            });
            return true;
        } catch (InvalidPaymentStateException | DataIntegrityViolationException referenceConflict) {
            return false;
        }
    }

    private void awaitPaymentReadBarrier(CyclicBarrier paymentReadBarrier) {
        try {
            paymentReadBarrier.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent payment interrupted", interruptedException);
        } catch (BrokenBarrierException | TimeoutException synchronizationException) {
            throw new IllegalStateException("Could not synchronize payment reads", synchronizationException);
        }
    }

    private ResultActions notifySuccessfulPayment(UUID orderId, UUID paymentId, String providerReference) throws Exception {
        var paymentSucceededRequest = new PaymentSucceededRequest(providerReference);
        return mockMvc.perform(patch(paymentSuccessPath(orderId, paymentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentSucceededRequest)));
    }

    private String paymentSuccessPath(UUID orderId, UUID paymentId) {
        return "/api/v1/orders/" + orderId + "/payments/" + paymentId + "/succeeded";
    }

    private UUID createProcessingPayment(UUID orderId, PaymentProvider provider) {
        var createPaymentRequest = new CreatePaymentAttemptRequest(provider, UUID.randomUUID().toString());
        var createdPaymentResponse = paymentService.createPaymentAttempt(orderId, createPaymentRequest);
        paymentService.startProcessing(orderId, createdPaymentResponse.id());
        return createdPaymentResponse.id();
    }

    private UUID persistProduct() {
        var savedProduct = productRepository.saveAndFlush(ProductEntity.builder()
                .sku(UUID.randomUUID().toString()).name("Product").price(new BigDecimal("100.00"))
                .quantity(10L).build());
        productIds.add(savedProduct.getId());
        return savedProduct.getId();
    }

    private UUID persistOrder(UUID productId) {
        return transactionTemplate.execute(status -> {
            var savedCustomer = customerRepository.saveAndFlush(
                    new CustomerEntity(UUID.randomUUID() + "@example.com", "Customer"));
            customerIds.add(savedCustomer.getId());
            String productSku = productRepository.findById(productId).orElseThrow().getSku();
            var orderItem = OrderItemEntity.builder().sku(productSku)
                    .unitPrice(new BigDecimal("100.00")).quantity(2L).build();
            var savedOrder = orderRepository.saveAndFlush(OrderEntity.builder()
                    .customerId(savedCustomer.getId()).addItem(orderItem).build());
            orderIds.add(savedOrder.getId());
            return savedOrder.getId();
        });
    }
}
