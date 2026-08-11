package com.orders.messages.orders_demo.app.payment.internal;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderNotFoundException;
import com.orders.messages.orders_demo.app.payment.api.CreatePaymentAttemptRequest;
import com.orders.messages.orders_demo.app.payment.api.PaymentFailedRequest;
import com.orders.messages.orders_demo.app.payment.api.PaymentSucceededRequest;
import com.orders.messages.orders_demo.app.payment.internal.exceptions.PaymentNotFoundException;

@WebMvcTest(PaymentAttemptController.class)
public class PaymentAttemptControllerTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentAttemptService paymentAttemptService;

    private UUID orderId;
    private UUID paymentId;

    private static final String DEFAULT_IDEMPOTENCY_KEY = "idempotency_key";
    private static final String DEFAULT_PROVIDER_REF = "provider_ref";

    @BeforeEach
    public void setup() {
        orderId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
    }

    @Test
    public void getAllPayments_ShouldReturn200() throws Exception {
        PaymentAttemptEntity paymentAttempt1 = createPaymentAttempt(paymentId, orderId);
        PaymentAttemptEntity paymentAttempt2 = createPaymentAttempt(paymentId, orderId);
        when(paymentAttemptService.getAllPayments(orderId))
                .thenReturn(List.of(paymentAttempt1, paymentAttempt2));

        mvc.perform(get("/api/v1/orders/{orderId}/payments", orderId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(paymentId.toString()))
                .andExpect(jsonPath("$[0].provider").value("NONE"))
                .andExpect(jsonPath("$[0].idempotencyKey").value(DEFAULT_IDEMPOTENCY_KEY))
                .andExpect(jsonPath("$[0].status").value("CREATED"))
                .andExpect(jsonPath("$[1].id").value(paymentId.toString()))
                .andExpect(jsonPath("$[1].provider").value("NONE"))
                .andExpect(jsonPath("$[1].idempotencyKey").value(DEFAULT_IDEMPOTENCY_KEY))
                .andExpect(jsonPath("$[1].status").value("CREATED"));
        verify(paymentAttemptService).getAllPayments(orderId);
    }

    @Test
    public void getAllPayments_WhenOrderNotFound_ShouldReturn404() throws Exception {
        when(paymentAttemptService.getAllPayments(orderId)).thenThrow(new OrderNotFoundException());

        mvc.perform(get("/api/v1/orders/{orderId}/payments", orderId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Order could not be found."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId + "/payments"));
        verify(paymentAttemptService).getAllPayments(orderId);
    }

    @Test
    public void getPayment_ShouldReturn200() throws Exception {
        PaymentAttemptEntity paymentAttempt = createPaymentAttempt(paymentId, orderId);
        when(paymentAttemptService.getPaymentAttempt(orderId, paymentId)).thenReturn(paymentAttempt);

        mvc.perform(get("/api/v1/orders/{orderId}/payments/{paymentId}", orderId, paymentId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.provider").value("NONE"))
                .andExpect(jsonPath("$.idempotencyKey").value(DEFAULT_IDEMPOTENCY_KEY))
                .andExpect(jsonPath("$.status").value("CREATED"));
        verify(paymentAttemptService).getPaymentAttempt(orderId, paymentId);
    }

    @Test
    void getPayment_WhenPaymentNotFound_ShouldReturn404() throws Exception {
        when(paymentAttemptService.getPaymentAttempt(orderId, paymentId))
                .thenThrow(new PaymentNotFoundException());

        mvc.perform(get("/api/v1/orders/{orderId}/payments/{paymentId}", orderId, paymentId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Payment attempt not found."))
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/orders/" + orderId + "/payments/" + paymentId));
    }

    @Test
    public void createPayment_WhenRequestIsValid_ShouldReturn201() throws Exception {
        CreatePaymentAttemptRequest request = new CreatePaymentAttemptRequest(
                PaymentProvider.NONE, DEFAULT_IDEMPOTENCY_KEY);
        PaymentAttemptEntity paymentAttempt = createPaymentAttempt(paymentId, orderId);
        when(paymentAttemptService.createPaymentAttempt(orderId, request)).thenReturn(paymentAttempt);

        mvc.perform(post("/api/v1/orders/{orderId}/payments", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.provider").value("NONE"))
                .andExpect(jsonPath("$.idempotencyKey").value(DEFAULT_IDEMPOTENCY_KEY))
                .andExpect(jsonPath("$.status").value("CREATED"));
        verify(paymentAttemptService).createPaymentAttempt(orderId, request);
    }

    @Test
    void createPayment_WhenPaymentNotFound_ShouldReturn404() throws Exception {
        CreatePaymentAttemptRequest request = new CreatePaymentAttemptRequest(
                PaymentProvider.NONE, DEFAULT_IDEMPOTENCY_KEY);
        when(paymentAttemptService.createPaymentAttempt(orderId, request))
                .thenThrow(new PaymentNotFoundException());

        mvc.perform(post("/api/v1/orders/{orderId}/payments", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Payment attempt not found."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId + "/payments"));
    }

    @Test
    public void createPayment_WhenValidationFailsWithEmptyProvider_ShouldReturn400() throws Exception {
        CreatePaymentAttemptRequest request = new CreatePaymentAttemptRequest(
                null, DEFAULT_IDEMPOTENCY_KEY);
        PaymentAttemptEntity paymentAttempt = createPaymentAttempt(paymentId, orderId);
        when(paymentAttemptService.createPaymentAttempt(orderId, request)).thenReturn(paymentAttempt);

        mvc.perform(post("/api/v1/orders/{orderId}/payments", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Payment must have provider."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId + "/payments"));
    }

    @Test
    public void createPayment_WhenValidationFailsWithEmptyIdempotencyKey_ShouldReturn400() throws Exception {
        CreatePaymentAttemptRequest request = new CreatePaymentAttemptRequest(
                PaymentProvider.NONE, null);
        PaymentAttemptEntity paymentAttempt = createPaymentAttempt(paymentId, orderId);
        when(paymentAttemptService.createPaymentAttempt(orderId, request)).thenReturn(paymentAttempt);

        mvc.perform(post("/api/v1/orders/{orderId}/payments", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Payment must have idempotency key."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId + "/payments"));
    }

    @Test
    public void startProcessing_ShouldReturn200() throws Exception {
        PaymentAttemptEntity paymentAttempt = createPaymentAttemptWithStatus(paymentId, orderId,
                PaymentStatus.PROCESSING);
        when(paymentAttemptService.startProcessing(orderId, paymentId)).thenReturn(paymentAttempt);

        mvc.perform(patch("/api/v1/orders/{orderId}/payments/{paymentId}/processing", orderId, paymentId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.provider").value("NONE"))
                .andExpect(jsonPath("$.idempotencyKey").value(DEFAULT_IDEMPOTENCY_KEY))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
        verify(paymentAttemptService).startProcessing(orderId, paymentId);
    }

    @Test
    public void markPaymentAsSucceeded_ShouldReturn200() throws Exception {
        PaymentSucceededRequest paymentSucceededRequest = new PaymentSucceededRequest(DEFAULT_PROVIDER_REF);
        PaymentAttemptEntity paymentAttempt = createPaymentAttemptWithStatus(paymentId, orderId,
                PaymentStatus.SUCCEEDED);
        when(paymentAttemptService.markAsSucceeded(orderId, paymentId, DEFAULT_PROVIDER_REF))
                .thenReturn(paymentAttempt);

        mvc.perform(patch("/api/v1/orders/{orderId}/payments/{paymentId}/succeeded", orderId, paymentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentSucceededRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.provider").value("NONE"))
                .andExpect(jsonPath("$.idempotencyKey").value(DEFAULT_IDEMPOTENCY_KEY))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        verify(paymentAttemptService).markAsSucceeded(orderId, paymentId, DEFAULT_PROVIDER_REF);
    }

    @Test
    public void markPaymentAsFailed_ShouldReturn200() throws Exception {
        PaymentFailedRequest paymentFailedRequest = new PaymentFailedRequest(500, "Internal error.");
        PaymentAttemptEntity paymentAttempt = createPaymentAttemptWithStatus(paymentId, orderId, PaymentStatus.FAILED);
        when(paymentAttemptService.markAsFailed(orderId, paymentId, 500, "Internal error."))
                .thenReturn(paymentAttempt);

        mvc.perform(patch("/api/v1/orders/{orderId}/payments/{paymentId}/failed", orderId, paymentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentFailedRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.provider").value("NONE"))
                .andExpect(jsonPath("$.idempotencyKey").value(DEFAULT_IDEMPOTENCY_KEY))
                .andExpect(jsonPath("$.status").value("FAILED"));
        verify(paymentAttemptService).markAsFailed(orderId, paymentId, 500, "Internal error.");
    }

    @Test
    public void markPaymentAsCancelled_ShouldReturn200() throws Exception {
        PaymentAttemptEntity paymentAttempt = createPaymentAttemptWithStatus(paymentId, orderId,
                PaymentStatus.CANCELLED);
        when(paymentAttemptService.markAsCancelled(orderId, paymentId)).thenReturn(paymentAttempt);

        mvc.perform(patch("/api/v1/orders/{orderId}/payments/{paymentId}/cancel", orderId, paymentId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.provider").value("NONE"))
                .andExpect(jsonPath("$.idempotencyKey").value(DEFAULT_IDEMPOTENCY_KEY))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        verify(paymentAttemptService).markAsCancelled(orderId, paymentId);
    }

    private static PaymentAttemptEntity createPaymentAttempt(UUID id, UUID orderId) {
        return PaymentAttemptEntity.builder()
                .id(id)
                .orderId(orderId)
                .idempotencyKey(DEFAULT_IDEMPOTENCY_KEY)
                .status(PaymentStatus.CREATED)
                .build();
    }

    private static PaymentAttemptEntity createPaymentAttemptWithStatus(UUID id, UUID orderId,
            PaymentStatus status) {
        return PaymentAttemptEntity.builder()
                .id(id)
                .orderId(orderId)
                .idempotencyKey(DEFAULT_IDEMPOTENCY_KEY)
                .status(status)
                .build();
    }

}
