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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    private PaymentAttemptService paymentAttemptService;

    private static final String DEFAULT_IDEMPOTENCY_KEY = "idempotency_key";
    private static final String DEFAULT_PROVIDER_REF = "providerRef";
    private static final BigDecimal DEFAULT_AMOUNT_TOTAL = BigDecimal.valueOf(100.00);

    private UUID orderId;
    private UUID paymentId;
    private ArgumentCaptor<PaymentAttemptEntity> paymentCaptor;

    @BeforeEach
    public void setup() {
        orderId = UUID.randomUUID();
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
        CreatePaymentAttemptRequest paymentRequest = new CreatePaymentAttemptRequest(
                PaymentProvider.NONE, DEFAULT_IDEMPOTENCY_KEY);
        when(orderInternalApi.getOrderDataForPayment(orderId)).thenReturn(new OrderData(orderId, DEFAULT_AMOUNT_TOTAL));
        mockPaymentRepositorySave();

        PaymentAttemptResponse result = paymentAttemptService.createPaymentAttempt(orderId, paymentRequest);

        verify(paymentAttemptRepository).save(paymentCaptor.capture());
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
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verify(paymentAttemptRepository, never()).findByIdempotencyKey(anyString());
    }

    @Test
    public void createPaymentAttempt_WhenIdempotencyKeyExists_ShouldReturnExistingPayment() {
        CreatePaymentAttemptRequest paymentRequest = new CreatePaymentAttemptRequest(PaymentProvider.NONE,
                DEFAULT_IDEMPOTENCY_KEY);
        PaymentAttemptEntity expectedPayment = createPaymentAttemptWithStatusAndOrder(orderId,
                PaymentStatus.PROCESSING);
        when(orderInternalApi.getOrderDataForPayment(orderId)).thenReturn(new OrderData(orderId, DEFAULT_AMOUNT_TOTAL));
        when(paymentAttemptRepository.findByIdempotencyKey(DEFAULT_IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(expectedPayment));

        PaymentAttemptResponse result = paymentAttemptService.createPaymentAttempt(orderId, paymentRequest);

        assertEquals(expectedPayment.getId(), result.id());
        verify(orderInternalApi).getOrderDataForPayment(orderId);
        verify(paymentAttemptRepository).findByIdempotencyKey(DEFAULT_IDEMPOTENCY_KEY);
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
    }

    /*
     * 
     * startProcessing
     * 
     */

    @Test
    public void startProcessing_WhenPaymentIsFoundAndOrderCanAcceptPayments_ShouldSavePaymentAsProcessing() {
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

        assertEquals("Only processing payments can be marked as succeeded.", result.getMessage());
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
