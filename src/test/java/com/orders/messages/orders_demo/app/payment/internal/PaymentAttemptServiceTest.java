package com.orders.messages.orders_demo.app.payment.internal;

import jakarta.persistence.EntityManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import com.orders.messages.orders_demo.app.product.api.ProductInternalApi;
import com.orders.messages.orders_demo.app.product.internal.exceptions.InsufficientStockException;
import com.orders.messages.orders_demo.app.order.api.OrderItemResponse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orders.messages.orders_demo.app.customer.api.CustomerInternalApi;
import com.orders.messages.orders_demo.app.customer.api.CustomerResponse;
import com.orders.messages.orders_demo.app.customer.internal.CustomerStatus;
import com.orders.messages.orders_demo.app.order.api.OrderResponse;
import com.orders.messages.orders_demo.app.order.api.OrderData;
import com.orders.messages.orders_demo.app.order.api.OrderInternalApi;
import com.orders.messages.orders_demo.app.order.internal.exceptions.InvalidOrderStateException;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderNotFoundException;
import com.orders.messages.orders_demo.app.payment.api.CreatePaymentAttemptRequest;
import com.orders.messages.orders_demo.app.payment.api.PaymentAttemptResponse;
import com.orders.messages.orders_demo.app.payment.internal.exceptions.InvalidPaymentStateException;
import com.orders.messages.orders_demo.app.payment.internal.exceptions.PaymentNotFoundException;

@ExtendWith(MockitoExtension.class)
public class PaymentAttemptServiceTest {

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;
    @Mock
    private OrderInternalApi orderInternalApi;

    @Mock
    private CustomerInternalApi customerInternalApi;

    @Mock
    private ProductInternalApi productInternalApi;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private PaymentAttemptService paymentAttemptService;

    private static final String DEFAULT_IDEMPOTENCY_KEY = "idempotency_key";
    private static final String DEFAULT_PROVIDER_REF = "providerRef";
    private static final BigDecimal DEFAULT_AMOUNT_TOTAL = BigDecimal.valueOf(100.00);

    private UUID orderId;
    private UUID customerId;
    private UUID paymentId;
    private ArgumentCaptor<PaymentAttemptEntity> paymentCaptor;

    @BeforeEach
    public void setup() {
        orderId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        paymentCaptor = ArgumentCaptor.forClass(PaymentAttemptEntity.class);
    }

    @Test
    public void getAllPayments_WhenOrderExists_ReturnsPaymentAttempts() {
        PaymentAttemptEntity payment1 = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.CREATED);
        PaymentAttemptEntity payment2 = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.PROCESSING);
        doNothing().when(orderInternalApi).validateOrderExists(orderId);
        when(paymentAttemptRepository.findByOrderId(orderId)).thenReturn(List.of(payment1, payment2));

        List<PaymentAttemptResponse> result = paymentAttemptService.getAllPayments(orderId);

        assertEquals(2, result.size());
        assertEquals(payment1.getId(), result.get(0).id());
        assertEquals(payment2.getId(), result.get(1).id());
        assertEquals(payment1.getStatus(), result.get(0).status());
        assertEquals(payment2.getStatus(), result.get(1).status());
        assertEquals(payment1.getIdempotencyKey(), result.get(0).idempotencyKey());
        assertEquals(payment2.getIdempotencyKey(), result.get(1).idempotencyKey());
        verify(orderInternalApi).validateOrderExists(orderId);
        verify(paymentAttemptRepository).findByOrderId(orderId);
    }

    @Test
    public void getAllPayments_WhenOrderDoesNotExist_ThrowsOrderNotFoundException() {
        doThrow(new OrderNotFoundException()).when(orderInternalApi).validateOrderExists(orderId);

        OrderNotFoundException result = assertThrows(OrderNotFoundException.class,
                () -> paymentAttemptService.getAllPayments(orderId));

        assertEquals("Order could not be found.", result.getMessage());

        verify(orderInternalApi).validateOrderExists(orderId);
        verify(paymentAttemptRepository, never()).findByOrderId(orderId);
    }

    /*
     * 
     * getPaymentAttempt
     * 
     */

    @Test
    public void getPaymentAttempt_WhenPaymentExists_ReturnsPayment() {
        PaymentAttemptEntity payment = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.CREATED);
        paymentId = payment.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        PaymentAttemptResponse result = paymentAttemptService.getPaymentAttempt(orderId, paymentId);

        assertEquals(paymentId, result.id());
        verify(paymentAttemptRepository).findById(paymentId);
    }

    @Test
    public void getPaymentAttempt_WhenPaymentDoesNotExist_ThrowsPaymentNotFoundException() {
        PaymentNotFoundException result = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.getPaymentAttempt(orderId, paymentId));

        assertEquals("Payment attempt not found.", result.getMessage());
        verify(paymentAttemptRepository).findById(paymentId);
    }

    @Test
    public void getPaymentAttempt_WhenPaymentOrderIdNotEqualToOrderId_ThrowsPaymentNotFoundException() {
        PaymentAttemptEntity payment = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.CREATED);
        UUID otherOrderId = UUID.randomUUID();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        PaymentNotFoundException result = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.getPaymentAttempt(otherOrderId, paymentId));

        assertEquals("Payment attempt not found.", result.getMessage());
        verify(paymentAttemptRepository).findById(paymentId);
    }

    /*
     * 
     * createPaymentAttempt
     * 
     */

    @Test
    public void createPaymentAttempt_WhenOrderExistsAndIdempotencyIsUnique_ShouldSavePayment() {
        mockCustomer(CustomerStatus.ACTIVE);
        CreatePaymentAttemptRequest paymentRequest = new CreatePaymentAttemptRequest(
                PaymentProvider.NONE, DEFAULT_IDEMPOTENCY_KEY);
        when(orderInternalApi.getOrderDataForPayment(orderId))
                .thenReturn(new OrderData(orderId, customerId, DEFAULT_AMOUNT_TOTAL));
        var orderItemResponse = createOrderItemResponse("SKU-RESERVE", 2L);
        when(orderInternalApi.getAllOrderItemsFromOrder(orderId)).thenReturn(List.of(orderItemResponse));
        mockPaymentRepositorySave();

        PaymentAttemptResponse result = paymentAttemptService.createPaymentAttempt(orderId, paymentRequest);

        verify(paymentAttemptRepository).save(paymentCaptor.capture());
        verify(orderInternalApi).markAsPaymentInProgress(orderId);
        verify(productInternalApi).decreaseProductStock("SKU-RESERVE", 2L);
        assertEquals(paymentCaptor.getValue().getId(), result.id());
        assertEquals(orderId, result.orderId());
        assertEquals(DEFAULT_IDEMPOTENCY_KEY, result.idempotencyKey());
        assertEquals(PaymentStatus.CREATED, result.status());
        assertEquals(PaymentProvider.NONE, result.provider());
    }

    @Test
    public void createPaymentAttempt_WhenOrderDoesNotExists_ShouldThrowOrderNotFoundException() {
        CreatePaymentAttemptRequest paymentRequest = new CreatePaymentAttemptRequest(
                PaymentProvider.NONE, DEFAULT_IDEMPOTENCY_KEY);
        when(orderInternalApi.getOrderDataForPayment(orderId)).thenThrow(new OrderNotFoundException());

        OrderNotFoundException result = assertThrows(OrderNotFoundException.class,
                () -> paymentAttemptService.createPaymentAttempt(orderId, paymentRequest));

        assertEquals("Order could not be found.", result.getMessage());
        verify(paymentAttemptRepository).findByIdempotencyKey(DEFAULT_IDEMPOTENCY_KEY);
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
    }

    @Test
    public void createPaymentAttempt_WhenIdempotencyKeyExistsForSameOrder_ShouldReturnExistingPayment() {
        CreatePaymentAttemptRequest paymentRequest = new CreatePaymentAttemptRequest(PaymentProvider.NONE,
                DEFAULT_IDEMPOTENCY_KEY);
        PaymentAttemptEntity expectedPayment = createPaymentAttemptWithStatusAndOrder(orderId,
                PaymentStatus.PROCESSING);
        when(paymentAttemptRepository.findByIdempotencyKey(DEFAULT_IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(expectedPayment));

        PaymentAttemptResponse result = paymentAttemptService.createPaymentAttempt(orderId, paymentRequest);

        assertEquals(expectedPayment.getId(), result.id());
        verify(paymentAttemptRepository).findByIdempotencyKey(DEFAULT_IDEMPOTENCY_KEY);
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(orderInternalApi, productInternalApi);
    }

    @Test
    public void createPaymentAttempt_WhenIdempotencyKeyExistsForAnotherOrder_ShouldRejectWithoutSaving() {
        UUID otherOrderId = UUID.randomUUID();
        CreatePaymentAttemptRequest request = new CreatePaymentAttemptRequest(PaymentProvider.NONE,
                DEFAULT_IDEMPOTENCY_KEY);
        PaymentAttemptEntity existingPayment = createPaymentAttemptWithStatusAndOrder(otherOrderId,
                PaymentStatus.CREATED);
        when(paymentAttemptRepository.findByIdempotencyKey(DEFAULT_IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existingPayment));

        InvalidPaymentStateException result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.createPaymentAttempt(orderId, request));

        assertEquals("Requested order ID is not equal to payment's order.", result.getMessage());
        assertEquals(otherOrderId, existingPayment.getOrderId());
        assertEquals(PaymentStatus.CREATED, existingPayment.getStatus());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(orderInternalApi, productInternalApi);
    }

    /*
     * 
     * startProcessing
     * 
     */

    @Test
    void startProcessing_WhenPaymentIsCreated_ShouldUpdateAndReturnRefreshedPaymentWithoutSaving() {
        mockCustomer(CustomerStatus.ACTIVE);
        when(orderInternalApi.getOrderById(orderId)).thenReturn(OrderResponse.builder()
                .setId(orderId).customerId(customerId).build());
        var createdPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.CREATED);
        paymentId = createdPaymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(createdPaymentAttempt));
        when(paymentAttemptRepository.startProcessingIfAvailable(paymentId)).thenReturn(1);
        mockRefreshedPaymentStatus(createdPaymentAttempt, PaymentStatus.PROCESSING);

        var processingPaymentResponse = paymentAttemptService.startProcessing(orderId, paymentId);

        InOrder processingSequence = inOrder(paymentAttemptRepository, entityManager);
        processingSequence.verify(paymentAttemptRepository).startProcessingIfAvailable(paymentId);
        processingSequence.verify(entityManager).refresh(createdPaymentAttempt);
        verify(orderInternalApi).validateCanReceivePayment(orderId);
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(productInternalApi);
        assertEquals(paymentId, processingPaymentResponse.id());
        assertEquals(PaymentStatus.PROCESSING, processingPaymentResponse.status());
        assertEquals(DEFAULT_IDEMPOTENCY_KEY, processingPaymentResponse.idempotencyKey());
    }

    @Test
    void startProcessing_WhenPaymentIsNotFound_ShouldRejectWithoutUpdating() {
        paymentId = UUID.randomUUID();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.empty());

        var paymentNotFoundException = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.startProcessing(orderId, paymentId));

        assertEquals("Payment attempt not found.", paymentNotFoundException.getMessage());
        verify(paymentAttemptRepository, never()).startProcessingIfAvailable(any(UUID.class));
        verifyNoInteractions(orderInternalApi, customerInternalApi, productInternalApi, entityManager);
    }

    @Test
    void startProcessing_WhenPaymentBelongsToAnotherOrder_ShouldRejectWithoutUpdating() {
        var createdPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.CREATED);
        paymentId = createdPaymentAttempt.getId();
        UUID otherOrderId = UUID.randomUUID();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(createdPaymentAttempt));

        var paymentNotFoundException = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.startProcessing(otherOrderId, paymentId));

        assertEquals("Payment attempt not found.", paymentNotFoundException.getMessage());
        verify(paymentAttemptRepository, never()).startProcessingIfAvailable(any(UUID.class));
        verifyNoInteractions(orderInternalApi, customerInternalApi, productInternalApi, entityManager);
    }

    @Test
    void startProcessing_WhenOrderCannotReceivePayment_ShouldRejectBeforeUpdating() {
        var createdPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.CREATED);
        paymentId = createdPaymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(createdPaymentAttempt));
        doThrow(new InvalidOrderStateException("This order cannot receive payment attempts."))
                .when(orderInternalApi).validateCanReceivePayment(orderId);

        var invalidOrderStateException = assertThrows(InvalidOrderStateException.class,
                () -> paymentAttemptService.startProcessing(orderId, paymentId));

        assertEquals("This order cannot receive payment attempts.", invalidOrderStateException.getMessage());
        verify(paymentAttemptRepository, never()).startProcessingIfAvailable(any(UUID.class));
        verifyNoInteractions(customerInternalApi, productInternalApi, entityManager);
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void startProcessing_WhenConditionalUpdateAffectsNoRows_ShouldRejectWithoutRefreshing(PaymentStatus initialStatus) {
        mockCustomer(CustomerStatus.ACTIVE);
        when(orderInternalApi.getOrderById(orderId)).thenReturn(OrderResponse.builder()
                .setId(orderId).customerId(customerId).build());
        var originalPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, initialStatus);
        paymentId = originalPaymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(originalPaymentAttempt));
        when(paymentAttemptRepository.startProcessingIfAvailable(paymentId)).thenReturn(0);

        var invalidPaymentStateException = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.startProcessing(orderId, paymentId));

        assertEquals("Only created payments can start processing.", invalidPaymentStateException.getMessage());
        assertEquals(initialStatus, originalPaymentAttempt.getStatus());
        verify(paymentAttemptRepository).startProcessingIfAvailable(paymentId);
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(entityManager, productInternalApi);
    }

    /*
     * 
     * markAsSucceeded
     * 
     */

    @Test
    public void markAsSucceeded_WhenProcessingPaymentIsFoundAndOrderCanAcceptPayments_ShouldCompleteOrderAndPaymentAndGetProviderRef() {
        PaymentAttemptEntity paymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.PROCESSING);
        paymentId = paymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(paymentAttempt));
        when(paymentAttemptRepository.markAsSucceededIfAvailable(DEFAULT_IDEMPOTENCY_KEY, DEFAULT_PROVIDER_REF))
                .thenReturn(1);
        doAnswer(invocation -> {
            ReflectionTestUtils.setField(paymentAttempt, "status", PaymentStatus.SUCCEEDED);
            ReflectionTestUtils.setField(paymentAttempt, "providerRef", DEFAULT_PROVIDER_REF);
            return null;
        }).when(entityManager).refresh(paymentAttempt);

        PaymentAttemptResponse succeededPaymentResponse = paymentAttemptService.markAsSucceeded(
                orderId, paymentId, DEFAULT_PROVIDER_REF);

        InOrder successSequence = inOrder(paymentAttemptRepository, entityManager, orderInternalApi);
        successSequence.verify(paymentAttemptRepository).markAsSucceededIfAvailable(DEFAULT_IDEMPOTENCY_KEY,
                DEFAULT_PROVIDER_REF);
        successSequence.verify(entityManager).refresh(paymentAttempt);
        successSequence.verify(orderInternalApi).markAsPaid(orderId);
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(productInternalApi);
        assertEquals(paymentId, succeededPaymentResponse.id());
        assertEquals(PaymentStatus.SUCCEEDED, succeededPaymentResponse.status());
        assertEquals(DEFAULT_PROVIDER_REF, succeededPaymentResponse.providerRef());
    }

    @Test
    void markAsSucceeded_WhenPaymentIsNotFound_ShouldRejectWithoutUpdating() {
        paymentId = UUID.randomUUID();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.empty());

        var paymentNotFoundException = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.markAsSucceeded(orderId, paymentId, DEFAULT_PROVIDER_REF));

        assertEquals("Payment attempt not found.", paymentNotFoundException.getMessage());
        verify(paymentAttemptRepository, never()).markAsSucceededIfAvailable(anyString(), anyString());
        verifyNoInteractions(entityManager, orderInternalApi, productInternalApi);
    }

    @Test
    void markAsSucceeded_WhenPaymentBelongsToAnotherOrder_ShouldRejectWithoutUpdating() {
        var processingPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.PROCESSING);
        paymentId = processingPaymentAttempt.getId();
        UUID otherOrderId = UUID.randomUUID();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(processingPaymentAttempt));

        var paymentNotFoundException = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.markAsSucceeded(otherOrderId, paymentId, DEFAULT_PROVIDER_REF));

        assertEquals("Payment attempt not found.", paymentNotFoundException.getMessage());
        verify(paymentAttemptRepository, never()).markAsSucceededIfAvailable(anyString(), anyString());
        verifyNoInteractions(entityManager, orderInternalApi, productInternalApi);
    }

    @Test
    void markAsSucceeded_WhenOrderCannotBeMarkedPaid_ShouldPropagateRejectionAfterRefreshingPayment() {
        var processingPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.PROCESSING);
        paymentId = processingPaymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(processingPaymentAttempt));
        when(paymentAttemptRepository.markAsSucceededIfAvailable(DEFAULT_IDEMPOTENCY_KEY, DEFAULT_PROVIDER_REF))
                .thenReturn(1);
        doAnswer(invocation -> {
            ReflectionTestUtils.setField(processingPaymentAttempt, "status", PaymentStatus.SUCCEEDED);
            ReflectionTestUtils.setField(processingPaymentAttempt, "providerRef", DEFAULT_PROVIDER_REF);
            return null;
        }).when(entityManager).refresh(processingPaymentAttempt);
        doThrow(new InvalidOrderStateException("Only pending orders can be marked as paid."))
                .when(orderInternalApi).markAsPaid(orderId);

        var invalidOrderStateException = assertThrows(InvalidOrderStateException.class,
                () -> paymentAttemptService.markAsSucceeded(orderId, paymentId, DEFAULT_PROVIDER_REF));

        assertEquals("Only pending orders can be marked as paid.", invalidOrderStateException.getMessage());
        InOrder successSequence = inOrder(paymentAttemptRepository, entityManager, orderInternalApi);
        successSequence.verify(paymentAttemptRepository).markAsSucceededIfAvailable(DEFAULT_IDEMPOTENCY_KEY,
                DEFAULT_PROVIDER_REF);
        successSequence.verify(entityManager).refresh(processingPaymentAttempt);
        successSequence.verify(orderInternalApi).markAsPaid(orderId);
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(productInternalApi);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, mode = Mode.EXCLUDE, names = "SUCCEEDED")
    void markAsSucceeded_WhenConditionalUpdateAffectsNoRows_ShouldRejectWithoutEffects(PaymentStatus initialStatus) {
        var originalPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, initialStatus);
        paymentId = originalPaymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(originalPaymentAttempt));
        when(paymentAttemptRepository.markAsSucceededIfAvailable(DEFAULT_IDEMPOTENCY_KEY, DEFAULT_PROVIDER_REF))
                .thenReturn(0);

        var invalidPaymentStateException = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.markAsSucceeded(orderId, paymentId, DEFAULT_PROVIDER_REF));

        assertEquals("Only processing payments can be marked as succeeded.", invalidPaymentStateException.getMessage());
        assertEquals(initialStatus, originalPaymentAttempt.getStatus());
        verify(paymentAttemptRepository).markAsSucceededIfAvailable(DEFAULT_IDEMPOTENCY_KEY, DEFAULT_PROVIDER_REF);
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(entityManager, orderInternalApi, productInternalApi);
    }

    /*
     * 
     * markAsFailed
     * 
     */

    @Test
    public void markAsFailed_WhenProcessingPaymentIsFound_ShouldSaveErrorCodeAndMessage() {
        PaymentAttemptEntity paymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.PROCESSING);
        paymentId = paymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(paymentAttempt));
        mockPaymentRepositorySave();

        PaymentAttemptResponse result = paymentAttemptService.markAsFailed(orderId, paymentId, 500, "Server error");

        verify(paymentAttemptRepository).save(paymentCaptor.capture());
        assertEquals(paymentCaptor.getValue().getId(), result.id());
        assertEquals(PaymentStatus.FAILED, result.status());
        assertEquals(500, result.failureCode());
        assertEquals("Server error", result.failureMessage());
    }

    @Test
    public void markAsFailed_WhenProcessingPaymentNotIsFound_ShouldThrowPaymentNotFoundException() {
        paymentId = UUID.randomUUID();

        PaymentNotFoundException result = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.markAsFailed(orderId, paymentId, 500, "Server error"));

        assertEquals("Payment attempt not found.", result.getMessage());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
    }

    @Test
    public void markAsFailed_WhenPaymentOrderIdNotEqualToOrderId_ThrowsPaymentNotFoundException() {
        UUID otherOrderId = UUID.randomUUID();
        PaymentAttemptEntity payment = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.PROCESSING);
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        PaymentNotFoundException result = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.markAsFailed(otherOrderId, paymentId, 500, "Server error"));

        assertEquals("Payment attempt not found.", result.getMessage());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, mode = Mode.EXCLUDE, names = { "PROCESSING", "FAILED" })
    public void markAsFailed_WhenPaymentIsNotProcessing_ShouldThrowInvalidPaymentStateException(
            PaymentStatus paymentStatus) {
        PaymentAttemptEntity paymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, paymentStatus);
        paymentId = paymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(paymentAttempt));

        InvalidPaymentStateException result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.markAsFailed(orderId, paymentId, 500, "Server error"));

        assertEquals("Only processing payments can fail.", result.getMessage());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
    }

    /*
     * 
     * markAsCancelled
     * 
     */

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = { "CREATED", "PROCESSING" })
    void markAsCancelled_WhenPaymentCanBeCancelled_ShouldUpdateRefreshAndRestoreStock(PaymentStatus initialStatus) {
        var originalPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, initialStatus);
        paymentId = originalPaymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(originalPaymentAttempt));
        when(paymentAttemptRepository.cancelIfAvailable(paymentId)).thenReturn(1);
        mockRefreshedPaymentStatus(originalPaymentAttempt, PaymentStatus.CANCELLED);
        when(orderInternalApi.getAllOrderItemsFromOrder(orderId))
                .thenReturn(List.of(createOrderItemResponse("SKU-RESTORE", 2L)));

        var cancelledPaymentResponse = paymentAttemptService.markAsCancelled(orderId, paymentId);

        InOrder cancellationSequence = inOrder(paymentAttemptRepository, entityManager, productInternalApi,
                orderInternalApi);
        cancellationSequence.verify(paymentAttemptRepository).cancelIfAvailable(paymentId);
        cancellationSequence.verify(entityManager).refresh(originalPaymentAttempt);
        cancellationSequence.verify(productInternalApi).increaseProductStock("SKU-RESTORE", 2L);
        cancellationSequence.verify(orderInternalApi).markAsPending(orderId);
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        assertEquals(paymentId, cancelledPaymentResponse.id());
        assertEquals(PaymentStatus.CANCELLED, cancelledPaymentResponse.status());
    }

    @Test
    void markAsCancelled_WhenPaymentIsNotFound_ShouldRejectWithoutRestoringStock() {
        paymentId = UUID.randomUUID();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.empty());

        var paymentNotFoundException = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.markAsCancelled(orderId, paymentId));

        assertEquals("Payment attempt not found.", paymentNotFoundException.getMessage());
        verify(paymentAttemptRepository, never()).cancelIfAvailable(any(UUID.class));
        verifyNoInteractions(orderInternalApi, productInternalApi, entityManager);
    }

    @Test
    void markAsCancelled_WhenPaymentBelongsToAnotherOrder_ShouldRejectWithoutRestoringStock() {
        var processingPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.PROCESSING);
        paymentId = processingPaymentAttempt.getId();
        UUID otherOrderId = UUID.randomUUID();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(processingPaymentAttempt));

        var paymentNotFoundException = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.markAsCancelled(otherOrderId, paymentId));

        assertEquals("Payment attempt not found.", paymentNotFoundException.getMessage());
        verify(paymentAttemptRepository, never()).cancelIfAvailable(any(UUID.class));
        verifyNoInteractions(orderInternalApi, productInternalApi, entityManager);
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void markAsCancelled_WhenConditionalUpdateAffectsNoRows_ShouldRejectWithoutRestoringStock(
            PaymentStatus initialStatus) {
        var originalPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, initialStatus);
        paymentId = originalPaymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(originalPaymentAttempt));
        when(paymentAttemptRepository.cancelIfAvailable(paymentId)).thenReturn(0);

        var invalidPaymentStateException = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.markAsCancelled(orderId, paymentId));

        assertEquals("Only created or processing payments can be cancelled.",
                invalidPaymentStateException.getMessage());
        assertEquals(initialStatus, originalPaymentAttempt.getStatus());
        verify(paymentAttemptRepository).cancelIfAvailable(paymentId);
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(orderInternalApi, productInternalApi, entityManager);
    }

    @Test
    void markAsCancelled_WhenStockRestorationFails_ShouldPropagateWithoutReturningOrderToPending() {
        var createdPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.CREATED);
        paymentId = createdPaymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(createdPaymentAttempt));
        when(paymentAttemptRepository.cancelIfAvailable(paymentId)).thenReturn(1);
        mockRefreshedPaymentStatus(createdPaymentAttempt, PaymentStatus.CANCELLED);
        when(orderInternalApi.getAllOrderItemsFromOrder(orderId))
                .thenReturn(List.of(createOrderItemResponse("SKU-RESTORE", 2L)));
        doThrow(new IllegalStateException("Stock restoration failed."))
                .when(productInternalApi).increaseProductStock("SKU-RESTORE", 2L);

        var stockRestorationException = assertThrows(IllegalStateException.class,
                () -> paymentAttemptService.markAsCancelled(orderId, paymentId));

        assertEquals("Stock restoration failed.", stockRestorationException.getMessage());
        verify(orderInternalApi, never()).markAsPending(any(UUID.class));
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
    }

    private void mockPaymentRepositorySave() {
        when(paymentAttemptRepository.save(any(PaymentAttemptEntity.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void markAsSucceeded_WhenSameNotificationIsRepeated_ShouldReturnWithoutUpdatingPaymentOrOrder() {
        var succeededPaymentAttempt = createSucceededPaymentAttempt("ref-repeat");
        when(paymentAttemptRepository.findById(succeededPaymentAttempt.getId()))
                .thenReturn(Optional.of(succeededPaymentAttempt));

        var repeatedSuccessResponse = paymentAttemptService.markAsSucceeded(
                orderId, succeededPaymentAttempt.getId(), "ref-repeat");

        assertEquals(succeededPaymentAttempt.getId(), repeatedSuccessResponse.id());
        assertEquals(PaymentStatus.SUCCEEDED, repeatedSuccessResponse.status());
        assertEquals("ref-repeat", repeatedSuccessResponse.providerRef());
        verify(paymentAttemptRepository, never()).markAsSucceededIfAvailable(anyString(), anyString());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(entityManager, orderInternalApi, productInternalApi);
    }

    @Test
    void markAsFailed_WhenSameNotificationIsRepeated_ShouldReturnWithoutSavingOrUpdatingOrder() {
        var payment = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.PROCESSING);
        payment.markAsFailed(402, "Declined");
        when(paymentAttemptRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        var result = paymentAttemptService.markAsFailed(orderId, payment.getId(), 402, "Declined");

        assertEquals(payment.getId(), result.id());
        assertEquals(PaymentStatus.FAILED, result.status());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(orderInternalApi, productInternalApi);
    }

    @Test
    void createPaymentAttempt_WhenOrderCannotStartPayment_ShouldPropagateRejectionWithoutSaving() {
        mockCustomer(CustomerStatus.ACTIVE);
        var request = new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, DEFAULT_IDEMPOTENCY_KEY);
        when(orderInternalApi.getOrderDataForPayment(orderId))
                .thenReturn(new OrderData(orderId, customerId, BigDecimal.ZERO));
        doThrow(new InvalidOrderStateException("Order must contain items and have a positive total to start payment."))
                .when(orderInternalApi).markAsPaymentInProgress(orderId);

        var result = assertThrows(InvalidOrderStateException.class,
                () -> paymentAttemptService.createPaymentAttempt(orderId, request));

        assertEquals("Order must contain items and have a positive total to start payment.", result.getMessage());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
    }

    @Test
    void createPaymentAttempt_WhenCustomerIsBlocked_ShouldRejectWithoutSavingOrLockingOrder() {
        mockCustomer(CustomerStatus.BLOCKED);
        when(orderInternalApi.getOrderDataForPayment(orderId))
                .thenReturn(new OrderData(orderId, customerId, DEFAULT_AMOUNT_TOTAL));
        var request = new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, DEFAULT_IDEMPOTENCY_KEY);

        var result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.createPaymentAttempt(orderId, request));

        assertEquals("Blocked customers cannot start payments.", result.getMessage());
        verify(orderInternalApi, never()).markAsPaymentInProgress(any(UUID.class));
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
    }

    @Test
    void startProcessing_WhenCustomerIsBlocked_ShouldRejectWithoutChangingPayment() {
        var payment = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.CREATED);
        when(paymentAttemptRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(orderInternalApi.getOrderById(orderId)).thenReturn(OrderResponse.builder()
                .setId(orderId).customerId(customerId).build());
        mockCustomer(CustomerStatus.BLOCKED);

        var result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.startProcessing(orderId, payment.getId()));

        assertEquals("Blocked customers cannot start payments.", result.getMessage());
        assertEquals(PaymentStatus.CREATED, payment.getStatus());
        verify(paymentAttemptRepository, never()).startProcessingIfAvailable(any(UUID.class));
        verifyNoInteractions(entityManager, productInternalApi);
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verify(orderInternalApi, never()).getOrderDataForPayment(any(UUID.class));
    }

    @Test
    void createPaymentAttempt_WhenExistingKeyBelongsToSameOrder_ShouldNotRevalidateCustomer() {
        var payment = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.CREATED);
        when(paymentAttemptRepository.findByIdempotencyKey(DEFAULT_IDEMPOTENCY_KEY)).thenReturn(Optional.of(payment));

        var result = paymentAttemptService.createPaymentAttempt(orderId,
                new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, DEFAULT_IDEMPOTENCY_KEY));

        assertEquals(payment.getId(), result.id());
        verifyNoInteractions(customerInternalApi, orderInternalApi, productInternalApi);
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
    }

    private void mockCustomer(CustomerStatus status) {
        when(customerInternalApi.getCustomer(customerId)).thenReturn(CustomerResponse.builder()
                .id(customerId).name("Test Customer").email("customer@example.com").status(status).build());
    }

    @Test
    void createPaymentAttempt_WhenOrderHasMultipleProducts_ShouldReserveEveryItemBeforeSaving() {
        mockCustomer(CustomerStatus.ACTIVE);
        var createPaymentRequest = new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, DEFAULT_IDEMPOTENCY_KEY);
        when(orderInternalApi.getOrderDataForPayment(orderId))
                .thenReturn(new OrderData(orderId, customerId, DEFAULT_AMOUNT_TOTAL));
        when(orderInternalApi.getAllOrderItemsFromOrder(orderId)).thenReturn(List.of(
                createOrderItemResponse("SKU-FIRST", 2L), createOrderItemResponse("SKU-SECOND", 3L)));
        mockPaymentRepositorySave();

        var createdPaymentResponse = paymentAttemptService.createPaymentAttempt(orderId, createPaymentRequest);

        InOrder reservationSequence = inOrder(orderInternalApi, productInternalApi, paymentAttemptRepository);
        reservationSequence.verify(orderInternalApi).getAllOrderItemsFromOrder(orderId);
        reservationSequence.verify(productInternalApi).decreaseProductStock("SKU-FIRST", 2L);
        reservationSequence.verify(productInternalApi).decreaseProductStock("SKU-SECOND", 3L);
        reservationSequence.verify(orderInternalApi).markAsPaymentInProgress(orderId);
        reservationSequence.verify(paymentAttemptRepository).save(any(PaymentAttemptEntity.class));
        assertEquals(PaymentStatus.CREATED, createdPaymentResponse.status());
    }

    @Test
    void createPaymentAttempt_WhenReservationFails_ShouldPropagateFailureWithoutSaving() {
        mockCustomer(CustomerStatus.ACTIVE);
        var createPaymentRequest = new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, DEFAULT_IDEMPOTENCY_KEY);
        when(orderInternalApi.getOrderDataForPayment(orderId))
                .thenReturn(new OrderData(orderId, customerId, DEFAULT_AMOUNT_TOTAL));
        when(orderInternalApi.getAllOrderItemsFromOrder(orderId)).thenReturn(List.of(
                createOrderItemResponse("SKU-FIRST", 2L), createOrderItemResponse("SKU-SECOND", 3L)));
        doNothing().when(productInternalApi).decreaseProductStock("SKU-FIRST", 2L);
        doThrow(new InsufficientStockException()).when(productInternalApi).decreaseProductStock("SKU-SECOND", 3L);

        var reservationException = assertThrows(InsufficientStockException.class,
                () -> paymentAttemptService.createPaymentAttempt(orderId, createPaymentRequest));

        assertEquals("Insufficient stock.", reservationException.getMessage());
        verify(productInternalApi).decreaseProductStock("SKU-FIRST", 2L);
        verify(productInternalApi).decreaseProductStock("SKU-SECOND", 3L);
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
    }

    private OrderItemResponse createOrderItemResponse(String sku, long quantity) {
        return OrderItemResponse.builder().id(UUID.randomUUID()).orderId(orderId)
                .sku(sku).quantity(quantity).unitPrice(new BigDecimal("10.00"))
                .lineTotal(new BigDecimal("10.00").multiply(BigDecimal.valueOf(quantity))).build();
    }

    private static PaymentAttemptEntity createPaymentAttemptWithStatusAndOrder(UUID orderId,
            PaymentStatus status) {
        return PaymentAttemptEntity.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .idempotencyKey(DEFAULT_IDEMPOTENCY_KEY)
                .status(status)
                .build();
    }

    @Test
    void markAsSucceeded_WhenRepeatedReferenceDiffers_ShouldRejectWithoutEffects() {
        var succeededPaymentAttempt = createSucceededPaymentAttempt("original-reference");
        when(paymentAttemptRepository.findById(succeededPaymentAttempt.getId()))
                .thenReturn(Optional.of(succeededPaymentAttempt));

        var invalidPaymentStateException = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.markAsSucceeded(orderId, succeededPaymentAttempt.getId(),
                        "another-reference"));

        assertEquals("Successful payment has a different provider reference.",
                invalidPaymentStateException.getMessage());
        assertEquals("original-reference", succeededPaymentAttempt.getProviderRef());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verify(paymentAttemptRepository, never()).markAsSucceededIfAvailable(anyString(), anyString());
        verifyNoInteractions(entityManager, orderInternalApi, productInternalApi);
    }

    @Test
    void markAsFailed_WhenRepeatedFailureDetailsDiffer_ShouldRejectWithoutEffects() {
        var failedPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.PROCESSING);
        failedPaymentAttempt.markAsFailed(402, "Declined");
        when(paymentAttemptRepository.findById(failedPaymentAttempt.getId()))
                .thenReturn(Optional.of(failedPaymentAttempt));

        var invalidPaymentStateException = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.markAsFailed(orderId, failedPaymentAttempt.getId(), 500,
                        "Another failure"));

        assertEquals("Failed payment has different failure details.", invalidPaymentStateException.getMessage());
        assertEquals(402, failedPaymentAttempt.getFailureCode());
        assertEquals("Declined", failedPaymentAttempt.getFailureMessage());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(orderInternalApi, productInternalApi);
    }

    private PaymentAttemptEntity createSucceededPaymentAttempt(String providerReference) {
        return PaymentAttemptEntity.builder().id(UUID.randomUUID()).orderId(orderId)
                .idempotencyKey(DEFAULT_IDEMPOTENCY_KEY).status(PaymentStatus.SUCCEEDED)
                .providerRef(providerReference).build();
    }

    private void mockRefreshedPaymentStatus(PaymentAttemptEntity paymentAttempt, PaymentStatus refreshedStatus) {
        doAnswer(invocation -> {
            ReflectionTestUtils.setField(paymentAttempt, "status", refreshedStatus);
            return null;
        }).when(entityManager).refresh(paymentAttempt);
    }
}
