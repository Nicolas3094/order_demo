package com.orders.messages.orders_demo.services;

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
import org.junit.jupiter.params.provider.MethodSource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orders.messages.orders_demo.dtos.request.CreatePaymentAttemptRequest;
import com.orders.messages.orders_demo.entity.Customer;
import com.orders.messages.orders_demo.entity.Order;
import com.orders.messages.orders_demo.entity.PaymentAttempt;
import com.orders.messages.orders_demo.enums.OrderStatus;
import com.orders.messages.orders_demo.enums.PaymentProvider;
import com.orders.messages.orders_demo.enums.PaymentStatus;
import com.orders.messages.orders_demo.exceptions.orders.OrderNotFoundException;
import com.orders.messages.orders_demo.exceptions.payment.InvalidPaymentStateException;
import com.orders.messages.orders_demo.exceptions.payment.PaymentNotFoundException;
import com.orders.messages.orders_demo.repositories.OrderRepository;
import com.orders.messages.orders_demo.repositories.PaymentAttemptRepository;

@ExtendWith(MockitoExtension.class)
public class PaymentAttemptServiceTest {

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;
    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private PaymentAttemptService paymentAttemptService;

    private static final String DEFAULT_IDEMPOTENCY_KEY = "idempotency_key";
    private static final String DEFAULT_PROVIDER_REF = "providerRef";

    private UUID orderId;
    private UUID paymentId;

    @BeforeEach
    public void setup() {
        orderId = UUID.randomUUID();
    }

    /*
     * 
     * getPaymentAttempt
     * 
     */

    @Test
    public void getPaymentAttempt_WhenPaymentExists_ReturnsPayment() {
        Order order = createOrderWithId(orderId);
        PaymentAttempt payment = createPaymentAttemptWithStatusAndOrder(order, PaymentStatus.CREATED);
        paymentId = payment.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        PaymentAttempt result = paymentAttemptService.getPaymentAttempt(orderId, paymentId);

        assertEquals(paymentId, result.getId());
    }

    @Test
    public void getPaymentAttempt_WhenPaymentDoesNotExist_ThrowsPaymentNotFoundException() {
        PaymentNotFoundException result = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.getPaymentAttempt(orderId, paymentId));

        assertEquals("Payment attempt not found.", result.getMessage());
    }

    @Test
    public void getPaymentAttempt_WhenPaymentOrderIdNotEqualToOrderId_ThrowsPaymentNotFoundException() {
        Order order = createOrderWithId(orderId);
        PaymentAttempt payment = createPaymentAttemptWithStatusAndOrder(order, PaymentStatus.CREATED);
        UUID otherOrderId = UUID.randomUUID();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        PaymentNotFoundException result = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.getPaymentAttempt(otherOrderId, paymentId));

        assertEquals("Payment attempt not found.", result.getMessage());
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
        Order order = createOrderWithId(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentAttemptRepository.findByIdempotencyKey(DEFAULT_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        mockPaymentRepositorySave();

        PaymentAttempt result = paymentAttemptService.createPaymentAttempt(orderId, paymentRequest);

        assertEquals(order, result.getOrder());
        assertEquals(DEFAULT_IDEMPOTENCY_KEY, result.getIdempotencyKey());
        assertEquals(PaymentStatus.CREATED, result.getStatus());
        assertEquals(PaymentProvider.NONE, result.getProvider());
        verify(paymentAttemptRepository).save(result);
    }

    @Test
    public void createPaymentAttempt_WhenOrderDoesNotExists_ShouldThrowOrderNotFoundException() {
        CreatePaymentAttemptRequest paymentRequest = new CreatePaymentAttemptRequest(
                PaymentProvider.NONE, DEFAULT_IDEMPOTENCY_KEY);
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        OrderNotFoundException result = assertThrows(OrderNotFoundException.class,
                () -> paymentAttemptService.createPaymentAttempt(orderId, paymentRequest));

        assertEquals("Order could not be found.", result.getMessage());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttempt.class));
        verify(paymentAttemptRepository, never()).findByIdempotencyKey(anyString());
    }

    @Test
    public void createPaymentAttempt_WhenIdempotencyKeyExists_ShouldReturnExistingPayment() {
        CreatePaymentAttemptRequest paymentRequest = new CreatePaymentAttemptRequest(
                PaymentProvider.NONE, DEFAULT_IDEMPOTENCY_KEY);
        Order order = createOrderWithId(orderId);
        PaymentAttempt expectedPayment = createPaymentAttemptWithStatusAndOrder(order, PaymentStatus.PROCESSING);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentAttemptRepository.findByIdempotencyKey(DEFAULT_IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(expectedPayment));

        PaymentAttempt result = paymentAttemptService.createPaymentAttempt(orderId, paymentRequest);

        assertEquals(expectedPayment, result);
        verify(orderRepository).findById(orderId);
        verify(paymentAttemptRepository).findByIdempotencyKey(DEFAULT_IDEMPOTENCY_KEY);
        verify(paymentAttemptRepository, never()).save(any(PaymentAttempt.class));
    }

    /*
     * 
     * startProcessing
     * 
     */

    @Test
    public void startProcessing_WhenPaymentIsFoundAndOrderCanAcceptPayments_ShouldSavePaymentAsProcessing() {
        Order order = createOrderWithId(orderId);
        PaymentAttempt paymentAttempt = createPaymentAttemptWithStatusAndOrder(order, PaymentStatus.CREATED);
        paymentId = paymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(paymentAttempt));
        mockPaymentRepositorySave();

        PaymentAttempt result = paymentAttemptService.startProcessing(orderId, paymentId);

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
        verify(paymentAttemptRepository, never()).save(any(PaymentAttempt.class));
    }

    @Test
    public void startProcessing_WhenPaymentOrderIdNotEqualToOrderId_ThrowsPaymentNotFoundException() {
        Order order = createOrderWithId(orderId);
        PaymentAttempt payment = createPaymentAttemptWithStatusAndOrder(order, PaymentStatus.CREATED);
        UUID otherOrderId = UUID.randomUUID();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        PaymentNotFoundException result = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.startProcessing(otherOrderId, paymentId));

        assertEquals("Payment attempt not found.", result.getMessage());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = { "PAID", "CANCELLED", "EXPIRED", "REFUNDED" })
    public void startProcessing_WhenPaymentIsFoundAndOrderCannotAcceptPayments_ShouldThrowInvalidPaymentStateException(
            OrderStatus orderStatus) {
        Order order = createOrderWithStatusAndId(orderId, orderStatus);
        PaymentAttempt paymentAttempt = createPaymentAttemptWithStatusAndOrder(order, PaymentStatus.CREATED);
        paymentId = paymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(paymentAttempt));

        InvalidPaymentStateException result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.startProcessing(orderId, paymentId));

        assertEquals("Only pending orders can be processed.", result.getMessage());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttempt.class));
    }

    @Test
    public void startProcessing_WhenPaymentIsAlreadyProcessing_ShouldThrowInvalidPaymentStateException() {
        Order order = createOrderWithId(orderId);
        PaymentAttempt paymentAttempt = createPaymentAttemptWithStatusAndOrder(order, PaymentStatus.PROCESSING);
        paymentId = paymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(paymentAttempt));

        InvalidPaymentStateException result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.startProcessing(orderId, paymentId));

        assertEquals("Payment is already being processed.", result.getMessage());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttempt.class));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = { "SUCCEEDED", "FAILED", "CANCELLED" })
    public void startProcessing_WhenPaymentIsInTerminalState_ShouldThrowInvalidPaymentStateException(
            PaymentStatus paymentStatus) {
        Order order = createOrderWithId(orderId);
        PaymentAttempt paymentAttempt = createPaymentAttemptWithStatusAndOrder(order, paymentStatus);
        paymentId = paymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(paymentAttempt));

        InvalidPaymentStateException result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.startProcessing(orderId, paymentId));

        assertEquals("Only created payments can start processing.", result.getMessage());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttempt.class));
    }

    /*
     * 
     * markAsSucceeded
     * 
     */

    @Test
    public void markAsSucceeded_WhenProcessingPaymentIsFoundAndOrderCanAcceptPayments_ShouldCompleteOrderAndPaymentAndGetProviderRef() {
        Order order = createOrderWithId(orderId);
        PaymentAttempt paymentAttempt = createPaymentAttemptWithStatusAndOrder(order, PaymentStatus.PROCESSING);
        paymentId = paymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(paymentAttempt));
        mockPaymentRepositorySave();
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentAttempt result = paymentAttemptService.markAsSucceeded(orderId, paymentId, DEFAULT_PROVIDER_REF);

        assertEquals(PaymentStatus.SUCCEEDED, result.getStatus());
        assertEquals(DEFAULT_PROVIDER_REF, result.getProviderRef());
        assertEquals(OrderStatus.PAID, result.getOrder().getStatus());
        verify(paymentAttemptRepository).save(result);
        verify(orderRepository).save(result.getOrder());
    }

    @Test
    public void markAsSucceeded_WhenProcessingPaymentIsNotFound_ShouldThrowPaymentNotFoundException() {
        paymentId = UUID.randomUUID();

        PaymentNotFoundException result = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.markAsSucceeded(orderId, paymentId, DEFAULT_PROVIDER_REF));

        assertEquals("Payment attempt not found.", result.getMessage());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttempt.class));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    public void markAsSucceeded__WhenPaymentOrderIdNotEqualToOrderId_ThrowsPaymentNotFoundException() {
        Order order = createOrderWithId(orderId);
        PaymentAttempt payment = createPaymentAttemptWithStatusAndOrder(order, PaymentStatus.CREATED);
        UUID otherOrderId = UUID.randomUUID();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        PaymentNotFoundException result = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.markAsSucceeded(otherOrderId, paymentId, DEFAULT_PROVIDER_REF));

        assertEquals("Payment attempt not found.", result.getMessage());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = { "PAID", "CANCELLED", "EXPIRED", "REFUNDED" })
    public void markAsSucceeded_WhenOrderCannotAcceptPayments_ShouldThrowInvalidPaymentStateException(
            OrderStatus orderStatus) {
        Order order = createOrderWithStatusAndId(orderId, orderStatus);
        PaymentAttempt paymentAttempt = createPaymentAttemptWithStatusAndOrder(order, PaymentStatus.PROCESSING);
        paymentId = paymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(paymentAttempt));

        InvalidPaymentStateException result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.markAsSucceeded(orderId, paymentId, DEFAULT_PROVIDER_REF));

        assertEquals("Only pending orders can be marked as paid.", result.getMessage());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttempt.class));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = { "CREATED", "SUCCEEDED", "FAILED", "CANCELLED" })
    public void markAsSucceeded_WhenNonProcessingPaymentIsFoundAndOrderCanAcceptPayments_ShouldThrowInvalidPaymentStateException(
            PaymentStatus paymentStatus) {
        Order order = createOrderWithId(orderId);
        PaymentAttempt paymentAttempt = createPaymentAttemptWithStatusAndOrder(order, paymentStatus);
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(paymentAttempt));

        InvalidPaymentStateException result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.markAsSucceeded(orderId, paymentId, DEFAULT_PROVIDER_REF));

        assertEquals("Only processing payments can be marked as succeeded.", result.getMessage());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttempt.class));
        verify(orderRepository, never()).save(any(Order.class));
    }

    /*
     * 
     * markAsFailed
     * 
     */

    @Test
    public void markAsFailed_WhenProcessingPaymentIsFound_ShouldSaveErrorCodeAndMessage() {
        Order order = createOrderWithId(orderId);
        PaymentAttempt paymentAttempt = createPaymentAttemptWithStatusAndOrder(order, PaymentStatus.PROCESSING);
        paymentId = paymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(paymentAttempt));
        mockPaymentRepositorySave();

        PaymentAttempt result = paymentAttemptService.markAsFailed(orderId, paymentId, 500, "Server error");

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
        verify(paymentAttemptRepository, never()).save(any(PaymentAttempt.class));
    }

    @Test
    public void markAsFailed_WhenPaymentOrderIdNotEqualToOrderId_ThrowsPaymentNotFoundException() {
        UUID otherOrderId = UUID.randomUUID();
        Order order = createOrderWithId(orderId);
        PaymentAttempt payment = createPaymentAttemptWithStatusAndOrder(order, PaymentStatus.PROCESSING);
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        PaymentNotFoundException result = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.markAsFailed(otherOrderId, paymentId, 500, "Server error"));

        assertEquals("Payment attempt not found.", result.getMessage());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = { "CREATED", "SUCCEEDED", "FAILED", "CANCELLED" })
    public void markAsFailed_WhenPaymentIsNotProcessing_ShouldThrowInvalidPaymentStateException(
            PaymentStatus paymentStatus) {
        Order order = createOrderWithId(orderId);
        PaymentAttempt paymentAttempt = createPaymentAttemptWithStatusAndOrder(order, paymentStatus);
        paymentId = paymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(paymentAttempt));

        InvalidPaymentStateException result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.markAsFailed(orderId, paymentId, 500, "Server error"));

        assertEquals("Only processing payments can fail.", result.getMessage());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttempt.class));
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
        Order order = createOrderWithId(orderId);
        PaymentAttempt paymentAttempt = createPaymentAttemptWithStatusAndOrder(order, paymentStatus);
        paymentId = paymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(paymentAttempt));
        mockPaymentRepositorySave();

        PaymentAttempt result = paymentAttemptService.markAsCancelled(orderId, paymentId);

        assertEquals(PaymentStatus.CANCELLED, result.getStatus());
        verify(paymentAttemptRepository).save(result);
    }

    @Test
    public void markAsFailed_WhenPaymentNotIsFound_ShouldThrowPaymentNotFoundException() {
        paymentId = UUID.randomUUID();

        PaymentNotFoundException result = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.markAsCancelled(orderId, paymentId));

        assertEquals("Payment attempt not found.", result.getMessage());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttempt.class));
    }

    @Test
    public void markAsFailed__WhenPaymentOrderIdNotEqualToOrderId_ThrowsPaymentNotFoundException() {
        UUID otherOrderId = UUID.randomUUID();
        Order order = createOrderWithId(orderId);
        PaymentAttempt payment = createPaymentAttemptWithStatusAndOrder(order, PaymentStatus.PROCESSING);
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        PaymentNotFoundException result = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.markAsCancelled(otherOrderId, paymentId));

        assertEquals("Payment attempt not found.", result.getMessage());
    }

    @ParameterizedTest
    @MethodSource("invalidPaymentStateExceptionWhenPaymentIsCancelled")
    public void markAsFailed_WhenPaymentIsSucceeded_ShouldThrowInvalidPaymentStateException(
            PaymentStatus paymentStatus, String message) {
        Order order = createOrderWithId(orderId);
        PaymentAttempt paymentAttempt = createPaymentAttemptWithStatusAndOrder(order, paymentStatus);
        paymentId = paymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(paymentAttempt));

        InvalidPaymentStateException result = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.markAsCancelled(orderId, paymentId));

        assertEquals(message, result.getMessage());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttempt.class));
    }

    private void mockPaymentRepositorySave() {
        when(paymentAttemptRepository.save(any(PaymentAttempt.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static PaymentAttempt createPaymentAttemptWithStatusAndOrder(Order order, PaymentStatus status) {
        return new PaymentAttempt(order, PaymentProvider.NONE, DEFAULT_IDEMPOTENCY_KEY, status);
    }

    private static Order createOrderWithStatusAndId(UUID orderId, OrderStatus status) {
        return Order.builder().id(orderId).customer(new Customer("email", "name")).currency("MXN").status(status)
                .build();
        // return new Order(orderId, new Customer("email", "name"), "mxn", new
        // BigDecimal("12341.21"), status);
    }

    private static Order createOrderWithId(UUID id) {
        return Order.builder().id(id).customer(new Customer("email", "name")).currency("MXN").build();
        // return new Order(id, new Customer("email", "name"), "mxn", new
        // BigDecimal("12341.21"));
    }

    @SuppressWarnings("unused")
    private static Stream<Arguments> invalidPaymentStateExceptionWhenPaymentIsCancelled() {
        return Stream.of(
                Arguments.of(PaymentStatus.SUCCEEDED, "Successful payments cannot be cancelled."),
                Arguments.of(PaymentStatus.FAILED, "Failed payments cannot be cancelled."),
                Arguments.of(PaymentStatus.CANCELLED, "Payment is already cancelled."));
    }
}
