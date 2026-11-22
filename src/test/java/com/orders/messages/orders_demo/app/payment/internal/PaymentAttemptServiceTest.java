package com.orders.messages.orders_demo.app.payment.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.MethodSource;
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
    public void startProcessing_WhenPaymentIsFoundAndOrderCanAcceptPayments_ShouldSavePaymentAsProcessing() {
        mockCustomer(CustomerStatus.ACTIVE);
        when(orderInternalApi.getOrderById(orderId)).thenReturn(OrderResponse.builder()
                .setId(orderId).customerId(customerId).build());
        PaymentAttemptEntity paymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.CREATED);
        paymentId = paymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(paymentAttempt));
        mockPaymentRepositorySave();

        PaymentAttemptResponse result = paymentAttemptService.startProcessing(orderId, paymentId);

        verify(paymentAttemptRepository).save(paymentCaptor.capture());
        verify(orderInternalApi).validateCanReceivePayment(orderId);
        assertEquals(paymentCaptor.getValue().getId(), result.id());
        assertEquals(PaymentStatus.PROCESSING, result.status());
    }

    @Test
    public void startProcessing_WhenPaymentIsNotFound_ShouldThrowPaymentNotFoundException() {
        paymentId = UUID.randomUUID();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.empty());

        PaymentNotFoundException result = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.startProcessing(orderId, paymentId));

        assertEquals("Payment attempt not found.", result.getMessage());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
    }

    @Test
    public void startProcessing_WhenPaymentOrderIdNotEqualToOrderId_ThrowsPaymentNotFoundException() {
        PaymentAttemptEntity payment = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.CREATED);
        UUID otherOrderId = UUID.randomUUID();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        PaymentNotFoundException result = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.startProcessing(otherOrderId, paymentId));

        assertEquals("Payment attempt not found.", result.getMessage());
    }

    @Test
    void startProcessing_WhenOrderCannotAcceptPayments_ShouldThrowInvalidPaymentStateException() {
        PaymentAttemptEntity paymentAttempt = createPaymentAttemptWithStatusAndOrder(
                orderId, PaymentStatus.CREATED);
        paymentId = paymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(paymentAttempt));
        doThrow(new InvalidOrderStateException("This order cannot receive payment attempts."))
                .when(orderInternalApi).validateCanReceivePayment(orderId);

        InvalidOrderStateException result = assertThrows(InvalidOrderStateException.class,
                () -> paymentAttemptService.startProcessing(orderId, paymentId));

        assertEquals("This order cannot receive payment attempts.", result.getMessage());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
    }

    @Test
    public void startProcessing_WhenPaymentIsAlreadyProcessing_ShouldThrowInvalidPaymentStateException() {
        mockCustomer(CustomerStatus.ACTIVE);
        when(orderInternalApi.getOrderById(orderId)).thenReturn(OrderResponse.builder()
                .setId(orderId).customerId(customerId).build());
        PaymentAttemptEntity paymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.PROCESSING);
        paymentId = paymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(paymentAttempt));

        InvalidPaymentStateException result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.startProcessing(orderId, paymentId));

        assertEquals("Payment is already being processed.", result.getMessage());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = { "SUCCEEDED", "FAILED", "CANCELLED" })
    public void startProcessing_WhenPaymentIsInTerminalState_ShouldThrowInvalidPaymentStateException(
            PaymentStatus paymentStatus) {
        mockCustomer(CustomerStatus.ACTIVE);
        when(orderInternalApi.getOrderById(orderId)).thenReturn(OrderResponse.builder()
                .setId(orderId).customerId(customerId).build());
        PaymentAttemptEntity paymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, paymentStatus);
        paymentId = paymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(paymentAttempt));

        InvalidPaymentStateException result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.startProcessing(orderId, paymentId));

        assertEquals("Only created payments can start processing.", result.getMessage());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
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
        mockPaymentRepositorySave();
        doNothing().when(orderInternalApi).markAsPaid(orderId);

        PaymentAttemptResponse result = paymentAttemptService.markAsSucceeded(orderId, paymentId, DEFAULT_PROVIDER_REF);

        verify(orderInternalApi).markAsPaid(orderId);
        verify(paymentAttemptRepository).save(paymentCaptor.capture());
        assertEquals(paymentCaptor.getValue().getId(), result.id());
        assertEquals(PaymentStatus.SUCCEEDED, result.status());
        assertEquals(DEFAULT_PROVIDER_REF, result.providerRef());
    }

    @Test
    public void markAsSucceeded_WhenProcessingPaymentIsNotFound_ShouldThrowPaymentNotFoundException() {
        paymentId = UUID.randomUUID();

        PaymentNotFoundException result = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.markAsSucceeded(orderId, paymentId, DEFAULT_PROVIDER_REF));

        assertEquals("Payment attempt not found.", result.getMessage());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verify(orderInternalApi, never()).markAsPaid(orderId);
    }

    @Test
    public void markAsSucceeded__WhenPaymentOrderIdNotEqualToOrderId_ThrowsPaymentNotFoundException() {
        PaymentAttemptEntity payment = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.CREATED);
        UUID otherOrderId = UUID.randomUUID();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        PaymentNotFoundException result = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.markAsSucceeded(otherOrderId, paymentId, DEFAULT_PROVIDER_REF));

        assertEquals("Payment attempt not found.", result.getMessage());
    }

    @Test
    public void markAsSucceeded_WhenOrderCannotAcceptPayments_ShouldThrowInvalidPaymentStateException() {
        PaymentAttemptEntity paymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.PROCESSING);
        paymentId = paymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(paymentAttempt));
        doThrow(new InvalidOrderStateException("Only pending orders can be marked as paid."))
                .when(orderInternalApi).markAsPaid(orderId);

        InvalidOrderStateException result = assertThrows(InvalidOrderStateException.class,
                () -> paymentAttemptService.markAsSucceeded(orderId, paymentId, DEFAULT_PROVIDER_REF));

        assertEquals("Only pending orders can be marked as paid.", result.getMessage());
        verify(orderInternalApi).markAsPaid(orderId);
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, mode = Mode.EXCLUDE, names = { "PROCESSING" })
    public void markAsSucceeded_WhenNonProcessingPaymentIsFoundAndOrderCanAcceptPayments_ShouldThrowInvalidPaymentStateException(
            PaymentStatus paymentStatus) {
        PaymentAttemptEntity paymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, paymentStatus);
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(paymentAttempt));

        InvalidPaymentStateException result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.markAsSucceeded(orderId, paymentId, DEFAULT_PROVIDER_REF));

        assertEquals(paymentStatus == PaymentStatus.SUCCEEDED
                ? "Successful payment has a different provider reference."
                : "Only processing payments can be marked as succeeded.", result.getMessage());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verify(orderInternalApi, never()).markAsPaid(any(UUID.class));
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
    @EnumSource(value = PaymentStatus.class, mode = Mode.EXCLUDE, names = { "PROCESSING" })
    public void markAsFailed_WhenPaymentIsNotProcessing_ShouldThrowInvalidPaymentStateException(
            PaymentStatus paymentStatus) {
        PaymentAttemptEntity paymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, paymentStatus);
        paymentId = paymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(paymentAttempt));

        InvalidPaymentStateException result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.markAsFailed(orderId, paymentId, 500, "Server error"));

        assertEquals(paymentStatus == PaymentStatus.FAILED
                ? "Failed payment has different failure details."
                : "Only processing payments can fail.", result.getMessage());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
    }

    /*
     * 
     * markAsCancelled
     * 
     */

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = { "CREATED", "PROCESSING" })
    public void markAsCancelled_WhenProcessingOrCreatedPaymentIsFound_ShouldSavePaymentAsCancelled(
            PaymentStatus paymentStatus) {
        PaymentAttemptEntity paymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, paymentStatus);
        paymentId = paymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(paymentAttempt));
        mockPaymentRepositorySave();

        PaymentAttemptResponse result = paymentAttemptService.markAsCancelled(orderId, paymentId);

        verify(paymentAttemptRepository).save(paymentCaptor.capture());
        assertEquals(paymentCaptor.getValue().getId(), result.id());
        assertEquals(PaymentStatus.CANCELLED, result.status());
        verify(orderInternalApi).markAsPending(orderId);
    }

    @Test
    public void markAsFailed_WhenPaymentNotIsFound_ShouldThrowPaymentNotFoundException() {
        paymentId = UUID.randomUUID();

        PaymentNotFoundException result = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.markAsCancelled(orderId, paymentId));

        assertEquals("Payment attempt not found.", result.getMessage());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
    }

    @Test
    public void markAsFailed__WhenPaymentOrderIdNotEqualToOrderId_ThrowsPaymentNotFoundException() {
        UUID otherOrderId = UUID.randomUUID();
        PaymentAttemptEntity payment = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.PROCESSING);
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        PaymentNotFoundException result = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.markAsCancelled(otherOrderId, paymentId));

        assertEquals("Payment attempt not found.", result.getMessage());
    }

    @ParameterizedTest
    @MethodSource("invalidPaymentStateExceptionWhenPaymentIsCancelled")
    public void markAsFailed_WhenPaymentIsSucceeded_ShouldThrowInvalidPaymentStateException(
            PaymentStatus paymentStatus, String message) {
        PaymentAttemptEntity paymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, paymentStatus);
        paymentId = paymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(paymentAttempt));

        InvalidPaymentStateException result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.markAsCancelled(orderId, paymentId));

        assertEquals(message, result.getMessage());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
    }

    private void mockPaymentRepositorySave() {
        when(paymentAttemptRepository.save(any(PaymentAttemptEntity.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void markAsSucceeded_WhenSameNotificationIsRepeated_ShouldReturnWithoutSavingOrUpdatingOrder() {
        var payment = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.PROCESSING);
        payment.markAsSucceeded("ref-repeat");
        when(paymentAttemptRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        var result = paymentAttemptService.markAsSucceeded(orderId, payment.getId(), "ref-repeat");

        assertEquals(payment.getId(), result.id());
        assertEquals(PaymentStatus.SUCCEEDED, result.status());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(orderInternalApi, productInternalApi);
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

    @SuppressWarnings("unused")
    private static Stream<Arguments> invalidPaymentStateExceptionWhenPaymentIsCancelled() {
        return Stream.of(
                Arguments.of(PaymentStatus.SUCCEEDED, "Successful payments cannot be cancelled."),
                Arguments.of(PaymentStatus.FAILED, "Failed payments cannot be cancelled."),
                Arguments.of(PaymentStatus.CANCELLED, "Payment is already cancelled."));
    }
}
