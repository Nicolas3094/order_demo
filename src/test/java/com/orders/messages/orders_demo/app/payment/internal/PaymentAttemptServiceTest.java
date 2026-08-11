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

    @BeforeEach
    public void setup() {
        orderId = UUID.randomUUID();
    }

    @Test
    public void getAllPayments_WhenOrderExists_ReturnsPaymentAttempts() {
        PaymentAttemptEntity payment1 = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.CREATED);
        PaymentAttemptEntity payment2 = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.PROCESSING);
        doNothing().when(orderInternalApi).validateOrderExists(orderId);
        when(paymentAttemptRepository.findByOrderId(orderId)).thenReturn(List.of(payment1, payment2));

        List<PaymentAttemptEntity> result = paymentAttemptService.getAllPayments(orderId);

        assertEquals(2, result.size());
        assertEquals(payment1, result.get(0));
        assertEquals(payment2, result.get(1));
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

        PaymentAttemptEntity result = paymentAttemptService.getPaymentAttempt(orderId, paymentId);

        assertEquals(paymentId, result.getId());
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

        PaymentAttemptEntity result = paymentAttemptService.createPaymentAttempt(orderId, paymentRequest);

        assertEquals(orderId, result.getOrderId());
        assertEquals(DEFAULT_IDEMPOTENCY_KEY, result.getIdempotencyKey());
        assertEquals(PaymentStatus.CREATED, result.getStatus());
        assertEquals(PaymentProvider.NONE, result.getProvider());
        verify(paymentAttemptRepository).save(result);
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

        PaymentAttemptEntity result = paymentAttemptService.createPaymentAttempt(orderId, paymentRequest);

        assertEquals(expectedPayment, result);
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

        PaymentAttemptEntity result = paymentAttemptService.startProcessing(orderId, paymentId);

        assertEquals(PaymentStatus.PROCESSING, result.getStatus());
        verify(paymentAttemptRepository).save(result);
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

        PaymentAttemptEntity result = paymentAttemptService.markAsSucceeded(orderId, paymentId, DEFAULT_PROVIDER_REF);

        assertEquals(PaymentStatus.SUCCEEDED, result.getStatus());
        assertEquals(DEFAULT_PROVIDER_REF, result.getProviderRef());
        verify(paymentAttemptRepository).save(result);
        verify(orderInternalApi).markAsPaid(orderId);
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

        PaymentAttemptEntity result = paymentAttemptService.markAsFailed(orderId, paymentId, 500, "Server error");

        assertEquals(PaymentStatus.FAILED, result.getStatus());
        assertEquals(500, result.getFailureCode());
        assertEquals("Server error", result.getFailureMessage());
        verify(paymentAttemptRepository).save(result);
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

        PaymentAttemptEntity result = paymentAttemptService.markAsCancelled(orderId, paymentId);

        assertEquals(PaymentStatus.CANCELLED, result.getStatus());
        verify(paymentAttemptRepository).save(result);
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
