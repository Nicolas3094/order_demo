package com.orders.messages.orders_demo.app.payment.internal;


import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.mockito.invocation.InvocationOnMock;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import jakarta.persistence.OptimisticLockException;
import com.orders.messages.orders_demo.app.order.internal.OrderEntity;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Clock;
import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import com.orders.messages.orders_demo.app.product.api.ProductInternalApi;
import com.orders.messages.orders_demo.app.product.internal.exceptions.InsufficientStockException;
import com.orders.messages.orders_demo.app.product.internal.exceptions.ProductNotFoundException;
import com.orders.messages.orders_demo.app.order.api.OrderItemResponse;

import static org.mockito.ArgumentMatchers.*;
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
    private TransactionTemplate transactionTemplate;
    @Mock
    private Clock clock;

    @InjectMocks
    private PaymentAttemptService paymentAttemptService;

    private static final Instant TEST_NOW = Instant.parse("2030-01-01T12:00:00Z");
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
        lenient().when(transactionTemplate.execute(any())).thenAnswer(this::executePaymentTransaction);
        lenient().when(clock.instant()).thenReturn(TEST_NOW);
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
    void createPaymentAttempt_WhenExistingKeyBelongsToSameOrder_ShouldNotRevalidateCustomer() {
        var payment = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.CREATED);
        when(paymentAttemptRepository.findByIdempotencyKey(DEFAULT_IDEMPOTENCY_KEY)).thenReturn(Optional.of(payment));

        var result = paymentAttemptService.createPaymentAttempt(orderId,
                new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, DEFAULT_IDEMPOTENCY_KEY));

        assertEquals(payment.getId(), result.id());
        verifyNoInteractions(customerInternalApi, orderInternalApi, productInternalApi);
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
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

    @Test
    void createPaymentAttempt_WhenCommitReportsDuplicateIdempotencyKey_ShouldRecoverCommittedPayment() {
        var duplicateKeyFailure = createConstraintFailure("uk_idempotency_key");

        assertExistingPaymentReturnedAfterTransactionFailure(duplicateKeyFailure);
    }

    @Test
    void createPaymentAttempt_WhenOrderVersionConflicts_ShouldRecoverCommittedPayment() {
        var staleOrderFailure = new ObjectOptimisticLockingFailureException(OrderEntity.class, orderId);

        assertExistingPaymentReturnedAfterTransactionFailure(staleOrderFailure);
    }

    @Test
    void createPaymentAttempt_WhenJpaReportsVersionConflict_ShouldRecoverCommittedPayment() {
        var staleOrderFailure = new OptimisticLockException("Stale order");

        assertExistingPaymentReturnedAfterTransactionFailure(staleOrderFailure);
    }

    @Test
    void createPaymentAttempt_WhenWinningRequestAlreadyChangedOrderState_ShouldRecoverCommittedPayment() {
        var changedOrderFailure = new InvalidOrderStateException("Only pending orders can be modified.");

        assertExistingPaymentReturnedAfterTransactionFailure(changedOrderFailure);
    }

    @Test
    void createPaymentAttempt_WhenWinningRequestAlreadyReservedStock_ShouldRecoverCommittedPayment() {
        assertExistingPaymentReturnedAfterTransactionFailure(new InsufficientStockException());
    }

    @ParameterizedTest
    @ValueSource(strings = { "uk_provider_provider_ref", "uk_product_sku" })
    void createPaymentAttempt_WhenAnotherConstraintFails_ShouldPropagateWithoutRecovery(String constraintName) {
        var unrelatedConstraintFailure = createConstraintFailure(constraintName);
        doThrow(unrelatedConstraintFailure).when(transactionTemplate).execute(any());
        var createPaymentRequest = new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, DEFAULT_IDEMPOTENCY_KEY);

        var actualFailure = assertThrows(DataIntegrityViolationException.class,
                () -> paymentAttemptService.createPaymentAttempt(orderId, createPaymentRequest));

        assertSame(unrelatedConstraintFailure, actualFailure);
        verify(transactionTemplate).execute(any());
        verifyNoInteractions(paymentAttemptRepository, orderInternalApi, customerInternalApi, productInternalApi);
    }

    @Test
    void createPaymentAttempt_WhenOnlyErrorTextMentionsIdempotencyConstraint_ShouldNotRecover() {
        var unrelatedDatabaseFailure = new DataIntegrityViolationException("SQL mentions uk_idempotency_key");
        doThrow(unrelatedDatabaseFailure).when(transactionTemplate).execute(any());
        var createPaymentRequest = new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, DEFAULT_IDEMPOTENCY_KEY);

        var actualFailure = assertThrows(DataIntegrityViolationException.class,
                () -> paymentAttemptService.createPaymentAttempt(orderId, createPaymentRequest));

        assertSame(unrelatedDatabaseFailure, actualFailure);
        verify(transactionTemplate).execute(any());
        verifyNoInteractions(paymentAttemptRepository, orderInternalApi, customerInternalApi, productInternalApi);
    }

    @Test
    void createPaymentAttempt_WhenRecoveredKeyBelongsToAnotherOrder_ShouldRejectWithoutCreatingPayment() {
        var duplicateKeyFailure = createConstraintFailure("uk_idempotency_key");
        doThrow(duplicateKeyFailure).doAnswer(this::executePaymentTransaction).when(transactionTemplate).execute(any());
        var otherOrderPayment = createPaymentAttemptWithStatusAndOrder(UUID.randomUUID(), PaymentStatus.CREATED);
        when(paymentAttemptRepository.findByIdempotencyKey(DEFAULT_IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(otherOrderPayment));
        var createPaymentRequest = new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, DEFAULT_IDEMPOTENCY_KEY);

        var invalidPaymentStateException = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.createPaymentAttempt(orderId, createPaymentRequest));

        assertEquals("Requested order ID is not equal to payment's order.", invalidPaymentStateException.getMessage());
        verify(transactionTemplate, times(2)).execute(any());
        verify(paymentAttemptRepository).findByIdempotencyKey(DEFAULT_IDEMPOTENCY_KEY);
        verifyNoMoreInteractions(paymentAttemptRepository);
        verifyNoInteractions(orderInternalApi, customerInternalApi, productInternalApi);
    }

    @Test
    void createPaymentAttempt_WhenNoCommittedPaymentExistsAfterConflict_ShouldRethrowOriginalFailure() {
        var staleOrderFailure = new ObjectOptimisticLockingFailureException(OrderEntity.class, orderId);
        doThrow(staleOrderFailure).doAnswer(this::executePaymentTransaction).when(transactionTemplate).execute(any());
        when(paymentAttemptRepository.findByIdempotencyKey(DEFAULT_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        var createPaymentRequest = new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, DEFAULT_IDEMPOTENCY_KEY);

        var actualFailure = assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> paymentAttemptService.createPaymentAttempt(orderId, createPaymentRequest));

        assertSame(staleOrderFailure, actualFailure);
        verify(transactionTemplate, times(2)).execute(any());
        verify(paymentAttemptRepository).findByIdempotencyKey(DEFAULT_IDEMPOTENCY_KEY);
        verifyNoInteractions(orderInternalApi, customerInternalApi, productInternalApi);
    }

    @Test
    void createPaymentAttempt_WhenCallerOwnsFailedTransaction_ShouldPropagateWithoutRecovery() {
        var duplicateKeyFailure = createConstraintFailure("uk_idempotency_key");
        doThrow(duplicateKeyFailure).when(transactionTemplate).execute(any());
        var createPaymentRequest = new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, DEFAULT_IDEMPOTENCY_KEY);
        TransactionSynchronizationManager.setActualTransactionActive(true);

        try {
            var actualFailure = assertThrows(DataIntegrityViolationException.class,
                    () -> paymentAttemptService.createPaymentAttempt(orderId, createPaymentRequest));

            assertSame(duplicateKeyFailure, actualFailure);
            verify(transactionTemplate).execute(any());
            verifyNoInteractions(paymentAttemptRepository, orderInternalApi, customerInternalApi, productInternalApi);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    /*
     * 
     * startProcessing
     * 
     */

    @Test
    void startProcessing_WhenPaymentIsCreated_ShouldSaveProcessingPayment() {
        mockCustomer(CustomerStatus.ACTIVE);
        when(orderInternalApi.getOrderById(orderId)).thenReturn(OrderResponse.builder()
                .setId(orderId).customerId(customerId).build());
        var createdPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.CREATED);
        paymentId = createdPaymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(createdPaymentAttempt));
        mockPaymentRepositorySave();

        var processingPaymentResponse = paymentAttemptService.startProcessing(orderId, paymentId);

        verify(orderInternalApi).validateCanReceivePayment(orderId);
        verify(paymentAttemptRepository).save(createdPaymentAttempt);
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
        verifyNoInteractions(orderInternalApi, customerInternalApi, productInternalApi);
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
        verifyNoInteractions(orderInternalApi, customerInternalApi, productInternalApi);
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
        verifyNoInteractions(customerInternalApi, productInternalApi);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, mode = Mode.EXCLUDE, names = "CREATED")
    void startProcessing_WhenStateIsNotCreated_ShouldRejectWithoutSaving(PaymentStatus initialStatus) {
        mockCustomer(CustomerStatus.ACTIVE);
        when(orderInternalApi.getOrderById(orderId)).thenReturn(OrderResponse.builder()
                .setId(orderId).customerId(customerId).build());
        var originalPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, initialStatus);
        paymentId = originalPaymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(originalPaymentAttempt));

        var invalidPaymentStateException = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.startProcessing(orderId, paymentId));

        assertEquals("Only created payments can start processing.", invalidPaymentStateException.getMessage());
        assertEquals(initialStatus, originalPaymentAttempt.getStatus());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(productInternalApi);
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
        verifyNoInteractions(productInternalApi);
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verify(orderInternalApi, never()).getOrderDataForPayment(any(UUID.class));
    }

    /*
     * 
     * markAsSucceeded
     * 
     */

    @Test
    public void markAsSucceeded_WhenProcessingPaymentIsFoundAndOrderCanAcceptPayments_ShouldCompleteOrderAndPaymentAndGetProviderRef() {
        var processingPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.PROCESSING);
        paymentId = processingPaymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(processingPaymentAttempt));
        mockPaymentRepositorySave();

        var succeededPaymentResponse = paymentAttemptService.markAsSucceeded(orderId, paymentId, DEFAULT_PROVIDER_REF);

        InOrder successSequence = inOrder(orderInternalApi, paymentAttemptRepository);
        successSequence.verify(orderInternalApi).markAsPaid(orderId);
        successSequence.verify(paymentAttemptRepository).save(processingPaymentAttempt);
        verifyNoInteractions(productInternalApi);
        assertEquals(paymentId, succeededPaymentResponse.id());
        assertEquals(PaymentStatus.SUCCEEDED, succeededPaymentResponse.status());
        assertEquals(DEFAULT_PROVIDER_REF, succeededPaymentResponse.providerRef());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentProvider.class, names = { "STRIPE", "PAYPAL" })
    void markAsSucceeded_WhenReferenceBelongsToAnotherPaymentOfSameProvider_ShouldRejectWithoutEffects(
            PaymentProvider paymentProvider) {
        var processingPaymentAttempt = PaymentAttemptEntity.builder()
                .id(UUID.randomUUID()).orderId(orderId).provider(paymentProvider)
                .status(PaymentStatus.PROCESSING).idempotencyKey(DEFAULT_IDEMPOTENCY_KEY).build();
        paymentId = processingPaymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(processingPaymentAttempt));
        when(paymentAttemptRepository.existsByProviderAndProviderRef(paymentProvider, DEFAULT_PROVIDER_REF))
                .thenReturn(true);

        var invalidPaymentStateException = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.markAsSucceeded(orderId, paymentId, DEFAULT_PROVIDER_REF));

        assertEquals("A successful payment has the same provider reference.", invalidPaymentStateException.getMessage());
        assertEquals(PaymentStatus.PROCESSING, processingPaymentAttempt.getStatus());
        assertNull(processingPaymentAttempt.getProviderRef());
        InOrder referenceValidationSequence = inOrder(paymentAttemptRepository);
        referenceValidationSequence.verify(paymentAttemptRepository).findById(paymentId);
        referenceValidationSequence.verify(paymentAttemptRepository)
                .existsByProviderAndProviderRef(paymentProvider, DEFAULT_PROVIDER_REF);
        verifyNoMoreInteractions(paymentAttemptRepository);
        verifyNoInteractions(orderInternalApi, productInternalApi);
    }

    @Test
    void markAsSucceeded_WhenPaymentIsNotFound_ShouldRejectWithoutUpdating() {
        paymentId = UUID.randomUUID();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.empty());

        var paymentNotFoundException = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.markAsSucceeded(orderId, paymentId, DEFAULT_PROVIDER_REF));

        assertEquals("Payment attempt not found.", paymentNotFoundException.getMessage());
        verifyNoInteractions(orderInternalApi, productInternalApi);
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
        verifyNoInteractions(orderInternalApi, productInternalApi);
    }

    @Test
    void markAsSucceeded_WhenOrderCannotBeMarkedPaid_ShouldPropagateWithoutSaving() {
        var processingPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.PROCESSING);
        paymentId = processingPaymentAttempt.getId();
        var paymentAfterRollback = PaymentAttemptEntity.builder().id(paymentId).orderId(orderId)
                .idempotencyKey(DEFAULT_IDEMPOTENCY_KEY).status(PaymentStatus.PROCESSING).build();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(processingPaymentAttempt),
                Optional.of(paymentAfterRollback));
        doThrow(new InvalidOrderStateException("Only pending orders can be marked as paid."))
                .when(orderInternalApi).markAsPaid(orderId);

        var invalidOrderStateException = assertThrows(InvalidOrderStateException.class,
                () -> paymentAttemptService.markAsSucceeded(orderId, paymentId, DEFAULT_PROVIDER_REF));

        assertEquals("Only pending orders can be marked as paid.", invalidOrderStateException.getMessage());
        verify(orderInternalApi).markAsPaid(orderId);
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(productInternalApi);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, mode = Mode.EXCLUDE, names = { "PROCESSING", "SUCCEEDED" })
    void markAsSucceeded_WhenStateCannotSucceed_ShouldRejectWithoutEffects(PaymentStatus initialStatus) {
        var originalPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, initialStatus);
        paymentId = originalPaymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(originalPaymentAttempt));

        var invalidPaymentStateException = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.markAsSucceeded(orderId, paymentId, DEFAULT_PROVIDER_REF));

        assertEquals("Only processing payments can be marked as succeeded.", invalidPaymentStateException.getMessage());
        assertEquals(initialStatus, originalPaymentAttempt.getStatus());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(orderInternalApi, productInternalApi);
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
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verify(paymentAttemptRepository, never()).existsByProviderAndProviderRef(any(PaymentProvider.class), anyString());
        verifyNoInteractions(orderInternalApi, productInternalApi);
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
        verifyNoInteractions(orderInternalApi, productInternalApi);
    }

    /*
     * 
     * markAsFailed
     * 
     */

    @Test
    void markAsFailed_WhenProcessingPaymentIsFound_ShouldSaveFailureAndRestoreStock() {
        var processingPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.PROCESSING);
        paymentId = processingPaymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(processingPaymentAttempt));
        when(orderInternalApi.getAllOrderItemsFromOrder(orderId))
                .thenReturn(List.of(createOrderItemResponse("SKU-RESTORE", 2L)));
        mockPaymentRepositorySave();

        var failedPaymentResponse = paymentAttemptService.markAsFailed(orderId, paymentId, 402, "Declined");

        InOrder failureSequence = inOrder(orderInternalApi, productInternalApi, paymentAttemptRepository);
        failureSequence.verify(orderInternalApi).markAsPending(orderId);
        failureSequence.verify(productInternalApi).increaseProductStock("SKU-RESTORE", 2L);
        failureSequence.verify(paymentAttemptRepository).save(processingPaymentAttempt);
        assertEquals(paymentId, failedPaymentResponse.id());
        assertEquals(PaymentStatus.FAILED, failedPaymentResponse.status());
        assertEquals(402, failedPaymentResponse.failureCode());
        assertEquals("Declined", failedPaymentResponse.failureMessage());
    }

    @Test
    void markAsFailed_WhenPaymentIsNotFound_ShouldRejectWithoutUpdating() {
        paymentId = UUID.randomUUID();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.empty());

        var paymentNotFoundException = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.markAsFailed(orderId, paymentId, 402, "Declined"));

        assertEquals("Payment attempt not found.", paymentNotFoundException.getMessage());
        verifyNoInteractions(orderInternalApi, productInternalApi);
    }

    @Test
    void markAsFailed_WhenPaymentBelongsToAnotherOrder_ShouldRejectWithoutUpdating() {
        var processingPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.PROCESSING);
        paymentId = processingPaymentAttempt.getId();
        UUID otherOrderId = UUID.randomUUID();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(processingPaymentAttempt));

        var paymentNotFoundException = assertThrows(PaymentNotFoundException.class,
                () -> paymentAttemptService.markAsFailed(otherOrderId, paymentId, 402, "Declined"));

        assertEquals("Payment attempt not found.", paymentNotFoundException.getMessage());
        verifyNoInteractions(orderInternalApi, productInternalApi);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, mode = Mode.EXCLUDE, names = { "PROCESSING", "FAILED" })
    void markAsFailed_WhenStateCannotFail_ShouldRejectWithoutEffects(PaymentStatus initialStatus) {
        var originalPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, initialStatus);
        paymentId = originalPaymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(originalPaymentAttempt));

        var invalidPaymentStateException = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.markAsFailed(orderId, paymentId, 402, "Declined"));

        assertEquals("Only processing payments can fail.", invalidPaymentStateException.getMessage());
        assertEquals(initialStatus, originalPaymentAttempt.getStatus());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(orderInternalApi, productInternalApi);
    }

    @Test
    void markAsFailed_WhenSameNotificationIsRepeated_ShouldReturnWithoutUpdating() {
        var failedPaymentAttempt = PaymentAttemptEntity.builder().id(UUID.randomUUID()).orderId(orderId)
                .idempotencyKey(DEFAULT_IDEMPOTENCY_KEY).status(PaymentStatus.FAILED)
                .failureCode(402).failureMessage("Declined").build();
        when(paymentAttemptRepository.findById(failedPaymentAttempt.getId())).thenReturn(Optional.of(failedPaymentAttempt));

        var repeatedFailureResponse = paymentAttemptService.markAsFailed(
                orderId, failedPaymentAttempt.getId(), 402, "Declined");

        assertEquals(failedPaymentAttempt.getId(), repeatedFailureResponse.id());
        assertEquals(PaymentStatus.FAILED, repeatedFailureResponse.status());
        assertEquals(402, repeatedFailureResponse.failureCode());
        assertEquals("Declined", repeatedFailureResponse.failureMessage());
        verify(paymentAttemptRepository).findById(failedPaymentAttempt.getId());
        verifyNoMoreInteractions(paymentAttemptRepository);
        verifyNoInteractions(orderInternalApi, productInternalApi);
    }

    @Test
    void markAsFailed_WhenOrderCannotReturnToPending_ShouldPropagateWithoutRestoringStock() {
        var processingPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.PROCESSING);
        paymentId = processingPaymentAttempt.getId();
        var paymentAfterRollback = PaymentAttemptEntity.builder().id(paymentId).orderId(orderId)
                .idempotencyKey(DEFAULT_IDEMPOTENCY_KEY).status(PaymentStatus.PROCESSING).build();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(processingPaymentAttempt),
                Optional.of(paymentAfterRollback));
        doThrow(new InvalidOrderStateException("Order cannot return to pending."))
                .when(orderInternalApi).markAsPending(orderId);

        var invalidOrderStateException = assertThrows(InvalidOrderStateException.class,
                () -> paymentAttemptService.markAsFailed(orderId, paymentId, 402, "Declined"));

        assertEquals("Order cannot return to pending.", invalidOrderStateException.getMessage());
        verifyNoInteractions(productInternalApi);
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
    }

    @Test
    void markAsFailed_WhenStockRestorationFails_ShouldPropagateWithoutSavingPayment() {
        var processingPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.PROCESSING);
        paymentId = processingPaymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(processingPaymentAttempt));
        when(orderInternalApi.getAllOrderItemsFromOrder(orderId))
                .thenReturn(List.of(createOrderItemResponse("SKU-RESTORE", 2L)));
        doThrow(new IllegalStateException("Stock restoration failed."))
                .when(productInternalApi).increaseProductStock("SKU-RESTORE", 2L);

        var stockRestorationException = assertThrows(IllegalStateException.class,
                () -> paymentAttemptService.markAsFailed(orderId, paymentId, 402, "Declined"));

        assertEquals("Stock restoration failed.", stockRestorationException.getMessage());
        verify(orderInternalApi).markAsPending(orderId);
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
    }

    @ParameterizedTest
    @CsvSource({ "500, Declined", "402, Another failure", "500, Another failure" })
    void markAsFailed_WhenRepeatedFailureDetailsDiffer_ShouldRejectWithoutEffects(
            int repeatedFailureCode, String repeatedFailureMessage) {
        var failedPaymentAttempt = PaymentAttemptEntity.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .idempotencyKey(DEFAULT_IDEMPOTENCY_KEY)
                .status(PaymentStatus.FAILED)
                .failureCode(402)
                .failureMessage("Declined")
                .build();
        when(paymentAttemptRepository.findById(failedPaymentAttempt.getId()))
                .thenReturn(Optional.of(failedPaymentAttempt));

        var invalidPaymentStateException = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.markAsFailed(orderId, failedPaymentAttempt.getId(), repeatedFailureCode,
                        repeatedFailureMessage));

        assertEquals("Failed payment has different failure details.", invalidPaymentStateException.getMessage());
        assertEquals(PaymentStatus.FAILED, failedPaymentAttempt.getStatus());
        assertEquals(402, failedPaymentAttempt.getFailureCode());
        assertEquals("Declined", failedPaymentAttempt.getFailureMessage());
        verify(paymentAttemptRepository, times(2)).findById(failedPaymentAttempt.getId());
        verifyNoMoreInteractions(paymentAttemptRepository);
        verifyNoInteractions(orderInternalApi, productInternalApi);
    }

    /*
     * 
     * markAsCancelled
     * 
     */

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = { "CREATED", "PROCESSING" })
    void markAsCancelled_WhenPaymentCanBeCancelled_ShouldSaveCancellationAndRestoreStock(PaymentStatus initialStatus) {
        var originalPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, initialStatus);
        paymentId = originalPaymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(originalPaymentAttempt));
        when(orderInternalApi.getAllOrderItemsFromOrder(orderId))
                .thenReturn(List.of(createOrderItemResponse("SKU-RESTORE", 2L)));
        mockPaymentRepositorySave();

        var cancelledPaymentResponse = paymentAttemptService.markAsCancelled(orderId, paymentId);

        InOrder cancellationSequence = inOrder(productInternalApi, orderInternalApi, paymentAttemptRepository);
        cancellationSequence.verify(productInternalApi).increaseProductStock("SKU-RESTORE", 2L);
        cancellationSequence.verify(orderInternalApi).markAsPending(orderId);
        cancellationSequence.verify(paymentAttemptRepository).save(originalPaymentAttempt);
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
        verifyNoInteractions(orderInternalApi, productInternalApi);
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
        verifyNoInteractions(orderInternalApi, productInternalApi);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, mode = Mode.EXCLUDE, names = { "CREATED", "PROCESSING" })
    void markAsCancelled_WhenStateCannotBeCancelled_ShouldRejectWithoutEffects(PaymentStatus initialStatus) {
        var originalPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, initialStatus);
        paymentId = originalPaymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(originalPaymentAttempt));

        var invalidPaymentStateException = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.markAsCancelled(orderId, paymentId));

        assertEquals("Only created or processing payments can be cancelled.", invalidPaymentStateException.getMessage());
        assertEquals(initialStatus, originalPaymentAttempt.getStatus());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(orderInternalApi, productInternalApi);
    }

    @Test
    void markAsCancelled_WhenStockRestorationFails_ShouldPropagateWithoutReturningOrderToPending() {
        var createdPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.CREATED);
        paymentId = createdPaymentAttempt.getId();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(createdPaymentAttempt));
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

    @Test
    void markAsSucceeded_WhenCommitConflictsWithIdenticalSuccess_ShouldReturnCommittedPayment() {
        var committedPayment = createSucceededPaymentAttempt(DEFAULT_PROVIDER_REF);
        var stalePaymentFailure = new ObjectOptimisticLockingFailureException(PaymentAttemptEntity.class, committedPayment.getId());
        doThrow(stalePaymentFailure).doAnswer(this::executePaymentTransaction).when(transactionTemplate).execute(any());
        when(paymentAttemptRepository.findById(committedPayment.getId())).thenReturn(Optional.of(committedPayment));

        var recoveredSuccessResponse = paymentAttemptService.markAsSucceeded(orderId, committedPayment.getId(), DEFAULT_PROVIDER_REF);

        assertEquals(committedPayment.getId(), recoveredSuccessResponse.id());
        assertEquals(PaymentStatus.SUCCEEDED, recoveredSuccessResponse.status());
        assertEquals(DEFAULT_PROVIDER_REF, recoveredSuccessResponse.providerRef());
        verify(transactionTemplate, times(2)).execute(any());
        verify(paymentAttemptRepository).findById(committedPayment.getId());
        verifyNoMoreInteractions(paymentAttemptRepository);
        verifyNoInteractions(orderInternalApi, productInternalApi);
    }

    @Test
    void markAsSucceeded_WhenReferenceCheckObservesIdenticalCommittedSuccess_ShouldReturnCommittedPayment() {
        var committedPayment = createSucceededPaymentAttempt(DEFAULT_PROVIDER_REF);
        var concurrentReferenceFailure = new InvalidPaymentStateException("A successful payment has the same provider reference.");
        doThrow(concurrentReferenceFailure).doAnswer(this::executePaymentTransaction).when(transactionTemplate).execute(any());
        when(paymentAttemptRepository.findById(committedPayment.getId())).thenReturn(Optional.of(committedPayment));

        var recoveredSuccessResponse = paymentAttemptService.markAsSucceeded(orderId, committedPayment.getId(), DEFAULT_PROVIDER_REF);

        assertEquals(committedPayment.getId(), recoveredSuccessResponse.id());
        assertEquals(DEFAULT_PROVIDER_REF, recoveredSuccessResponse.providerRef());
        verify(transactionTemplate, times(2)).execute(any());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(orderInternalApi, productInternalApi);
    }

    @Test
    void markAsFailed_WhenCommitConflictsWithIdenticalFailure_ShouldReturnCommittedPaymentWithoutRestoringStock() {
        var committedPayment = createFailedPaymentAttempt(402, "Declined");
        var stalePaymentFailure = new OptimisticLockException("Stale payment");
        doThrow(stalePaymentFailure).doAnswer(this::executePaymentTransaction).when(transactionTemplate).execute(any());
        when(paymentAttemptRepository.findById(committedPayment.getId())).thenReturn(Optional.of(committedPayment));

        var recoveredFailureResponse = paymentAttemptService.markAsFailed(orderId, committedPayment.getId(), 402, "Declined");

        assertEquals(committedPayment.getId(), recoveredFailureResponse.id());
        assertEquals(PaymentStatus.FAILED, recoveredFailureResponse.status());
        assertEquals(402, recoveredFailureResponse.failureCode());
        assertEquals("Declined", recoveredFailureResponse.failureMessage());
        verify(transactionTemplate, times(2)).execute(any());
        verify(paymentAttemptRepository).findById(committedPayment.getId());
        verifyNoMoreInteractions(paymentAttemptRepository);
        verifyNoInteractions(orderInternalApi, productInternalApi);
    }

    @Test
    void markAsFailed_WhenOrderAlreadyReturnedToPendingForIdenticalFailure_ShouldReturnCommittedPayment() {
        var committedPayment = createFailedPaymentAttempt(402, "Declined");
        var changedOrderFailure = new InvalidOrderStateException("Order is already pending.");
        doThrow(changedOrderFailure).doAnswer(this::executePaymentTransaction).when(transactionTemplate).execute(any());
        when(paymentAttemptRepository.findById(committedPayment.getId())).thenReturn(Optional.of(committedPayment));

        var recoveredFailureResponse = paymentAttemptService.markAsFailed(orderId, committedPayment.getId(), 402, "Declined");

        assertEquals(committedPayment.getId(), recoveredFailureResponse.id());
        assertEquals(PaymentStatus.FAILED, recoveredFailureResponse.status());
        verify(transactionTemplate, times(2)).execute(any());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(orderInternalApi, productInternalApi);
    }

    @Test
    void markAsSucceeded_WhenCommittedReferenceDiffers_ShouldPropagateOriginalConflict() {
        var committedPayment = createSucceededPaymentAttempt("another-reference");
        var stalePaymentFailure = new OptimisticLockException("Stale payment");
        doThrow(stalePaymentFailure).doAnswer(this::executePaymentTransaction).when(transactionTemplate).execute(any());
        when(paymentAttemptRepository.findById(committedPayment.getId())).thenReturn(Optional.of(committedPayment));

        var actualFailure = assertThrows(OptimisticLockException.class,
                () -> paymentAttemptService.markAsSucceeded(orderId, committedPayment.getId(), DEFAULT_PROVIDER_REF));

        assertSame(stalePaymentFailure, actualFailure);
        verify(transactionTemplate, times(2)).execute(any());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(orderInternalApi, productInternalApi);
    }

    @ParameterizedTest
    @CsvSource({ "500, Declined", "402, Another failure", "500, Another failure" })
    void markAsFailed_WhenCommittedFailureDetailsDiffer_ShouldPropagateOriginalConflict(int notifiedCode, String notifiedMessage) {
        var committedPayment = createFailedPaymentAttempt(402, "Declined");
        var stalePaymentFailure = new OptimisticLockException("Stale payment");
        doThrow(stalePaymentFailure).doAnswer(this::executePaymentTransaction).when(transactionTemplate).execute(any());
        when(paymentAttemptRepository.findById(committedPayment.getId())).thenReturn(Optional.of(committedPayment));

        var actualFailure = assertThrows(OptimisticLockException.class,
                () -> paymentAttemptService.markAsFailed(orderId, committedPayment.getId(), notifiedCode, notifiedMessage));

        assertSame(stalePaymentFailure, actualFailure);
        verify(transactionTemplate, times(2)).execute(any());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(orderInternalApi, productInternalApi);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, mode = Mode.EXCLUDE, names = "SUCCEEDED")
    void markAsSucceeded_WhenCommittedStateIsNotSucceeded_ShouldPropagateOriginalConflict(PaymentStatus committedStatus) {
        var committedPayment = createPaymentAttemptWithStatusAndOrder(orderId, committedStatus);
        var stalePaymentFailure = new OptimisticLockException("Stale payment");
        doThrow(stalePaymentFailure).doAnswer(this::executePaymentTransaction).when(transactionTemplate).execute(any());
        when(paymentAttemptRepository.findById(committedPayment.getId())).thenReturn(Optional.of(committedPayment));

        var actualFailure = assertThrows(OptimisticLockException.class,
                () -> paymentAttemptService.markAsSucceeded(orderId, committedPayment.getId(), DEFAULT_PROVIDER_REF));

        assertSame(stalePaymentFailure, actualFailure);
        verifyNoInteractions(orderInternalApi, productInternalApi);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, mode = Mode.EXCLUDE, names = "FAILED")
    void markAsFailed_WhenCommittedStateIsNotFailed_ShouldPropagateOriginalConflict(PaymentStatus committedStatus) {
        var committedPayment = createPaymentAttemptWithStatusAndOrder(orderId, committedStatus);
        var stalePaymentFailure = new OptimisticLockException("Stale payment");
        doThrow(stalePaymentFailure).doAnswer(this::executePaymentTransaction).when(transactionTemplate).execute(any());
        when(paymentAttemptRepository.findById(committedPayment.getId())).thenReturn(Optional.of(committedPayment));

        var actualFailure = assertThrows(OptimisticLockException.class,
                () -> paymentAttemptService.markAsFailed(orderId, committedPayment.getId(), 402, "Declined"));

        assertSame(stalePaymentFailure, actualFailure);
        verifyNoInteractions(orderInternalApi, productInternalApi);
    }

    @Test
    void markAsSucceeded_WhenRecoveredPaymentBelongsToAnotherOrder_ShouldPropagateOriginalConflict() {
        var anotherOrderPayment = PaymentAttemptEntity.builder().id(UUID.randomUUID()).orderId(UUID.randomUUID())
                .status(PaymentStatus.SUCCEEDED).providerRef(DEFAULT_PROVIDER_REF).build();
        var stalePaymentFailure = new OptimisticLockException("Stale payment");
        doThrow(stalePaymentFailure).doAnswer(this::executePaymentTransaction).when(transactionTemplate).execute(any());
        when(paymentAttemptRepository.findById(anotherOrderPayment.getId())).thenReturn(Optional.of(anotherOrderPayment));

        var actualFailure = assertThrows(OptimisticLockException.class,
                () -> paymentAttemptService.markAsSucceeded(orderId, anotherOrderPayment.getId(), DEFAULT_PROVIDER_REF));

        assertSame(stalePaymentFailure, actualFailure);
        verifyNoInteractions(orderInternalApi, productInternalApi);
    }

    @Test
    void markAsSucceeded_WhenPaymentIsMissingAfterConflict_ShouldPropagateOriginalConflict() {
        paymentId = UUID.randomUUID();
        var stalePaymentFailure = new OptimisticLockException("Stale payment");
        doThrow(stalePaymentFailure).doAnswer(this::executePaymentTransaction).when(transactionTemplate).execute(any());
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.empty());

        var actualFailure = assertThrows(OptimisticLockException.class,
                () -> paymentAttemptService.markAsSucceeded(orderId, paymentId, DEFAULT_PROVIDER_REF));

        assertSame(stalePaymentFailure, actualFailure);
        verifyNoInteractions(orderInternalApi, productInternalApi);
    }

    @ParameterizedTest
    @ValueSource(strings = { "uk_provider_provider_ref", "uk_idempotency_key" })
    void markAsSucceeded_WhenCommitFailsForIntegrityConstraint_ShouldNotAttemptRecovery(String constraintName) {
        var integrityFailure = createConstraintFailure(constraintName);
        doThrow(integrityFailure).when(transactionTemplate).execute(any());
        paymentId = UUID.randomUUID();

        var actualFailure = assertThrows(DataIntegrityViolationException.class,
                () -> paymentAttemptService.markAsSucceeded(orderId, paymentId, DEFAULT_PROVIDER_REF));

        assertSame(integrityFailure, actualFailure);
        verify(transactionTemplate).execute(any());
        verifyNoInteractions(paymentAttemptRepository, orderInternalApi, productInternalApi);
    }

    @Test
    void markAsSucceeded_WhenCallerOwnsFailedTransaction_ShouldNotRecoverInsideIt() {
        var stalePaymentFailure = new OptimisticLockException("Stale payment");
        doThrow(stalePaymentFailure).when(transactionTemplate).execute(any());
        paymentId = UUID.randomUUID();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        try {
            var actualFailure = assertThrows(OptimisticLockException.class,
                    () -> paymentAttemptService.markAsSucceeded(orderId, paymentId, DEFAULT_PROVIDER_REF));

            assertSame(stalePaymentFailure, actualFailure);
            verify(transactionTemplate).execute(any());
            verifyNoInteractions(paymentAttemptRepository, orderInternalApi, productInternalApi);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private PaymentAttemptEntity createFailedPaymentAttempt(int failureCode, String failureMessage) {
        return PaymentAttemptEntity.builder().id(UUID.randomUUID()).orderId(orderId).status(PaymentStatus.FAILED)
                .idempotencyKey(DEFAULT_IDEMPOTENCY_KEY).failureCode(failureCode).failureMessage(failureMessage).build();
    }

    @ParameterizedTest
    @ValueSource(longs = { 900, 901, 1800 })
    void expirePaymentAttemptIfDue_WhenUnsentPaymentReachesDeadline_ShouldCancelRestoreStockAndReturnOrderToPending(
            long paymentAgeInSeconds) {
        var createdPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.CREATED);
        paymentId = createdPaymentAttempt.getId();
        ReflectionTestUtils.setField(createdPaymentAttempt, "createdAt", TEST_NOW.minusSeconds(paymentAgeInSeconds));
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(createdPaymentAttempt));
        when(orderInternalApi.getAllOrderItemsFromOrder(orderId))
                .thenReturn(List.of(createOrderItemResponse("SKU-EXPIRATION", 2L)));

        boolean expired = paymentAttemptService.expirePaymentAttemptIfDue(paymentId, TEST_NOW);

        assertEquals(true, expired);
        assertEquals(PaymentStatus.CANCELLED, createdPaymentAttempt.getStatus());
        InOrder expirationSequence = inOrder(productInternalApi, orderInternalApi, paymentAttemptRepository);
        expirationSequence.verify(productInternalApi).increaseProductStock("SKU-EXPIRATION", 2L);
        expirationSequence.verify(orderInternalApi).markAsPending(orderId);
        expirationSequence.verify(paymentAttemptRepository).save(createdPaymentAttempt);
        verifyNoInteractions(customerInternalApi, clock);
    }

    @ParameterizedTest
    @ValueSource(longs = { -60, 0, 899 })
    void expirePaymentAttemptIfDue_WhenDeadlineHasNotBeenReached_ShouldReturnFalseWithoutEffects(long paymentAgeInSeconds) {
        var createdPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.CREATED);
        paymentId = createdPaymentAttempt.getId();
        ReflectionTestUtils.setField(createdPaymentAttempt, "createdAt", TEST_NOW.minusSeconds(paymentAgeInSeconds));
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(createdPaymentAttempt));

        boolean expired = paymentAttemptService.expirePaymentAttemptIfDue(paymentId, TEST_NOW);

        assertEquals(false, expired);
        assertEquals(PaymentStatus.CREATED, createdPaymentAttempt.getStatus());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(orderInternalApi, productInternalApi, customerInternalApi);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, mode = Mode.EXCLUDE, names = "CREATED")
    void expirePaymentAttemptIfDue_WhenPaymentIsNotCreated_ShouldReturnFalseWithoutEffects(PaymentStatus initialStatus) {
        var originalPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, initialStatus);
        paymentId = originalPaymentAttempt.getId();
        ReflectionTestUtils.setField(originalPaymentAttempt, "createdAt", TEST_NOW.minusSeconds(1800));
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(originalPaymentAttempt));

        boolean expired = paymentAttemptService.expirePaymentAttemptIfDue(paymentId, TEST_NOW);

        assertEquals(false, expired);
        assertEquals(initialStatus, originalPaymentAttempt.getStatus());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(orderInternalApi, productInternalApi, customerInternalApi);
    }

    @Test
    void expirePaymentAttemptIfDue_WhenPaymentHasProviderReference_ShouldKeepReservation() {
        var createdPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.CREATED);
        paymentId = createdPaymentAttempt.getId();
        ReflectionTestUtils.setField(createdPaymentAttempt, "createdAt", TEST_NOW.minusSeconds(1800));
        ReflectionTestUtils.setField(createdPaymentAttempt, "providerRef", "known-provider-operation");
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(createdPaymentAttempt));

        boolean expired = paymentAttemptService.expirePaymentAttemptIfDue(paymentId, TEST_NOW);

        assertEquals(false, expired);
        assertEquals(PaymentStatus.CREATED, createdPaymentAttempt.getStatus());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(orderInternalApi, productInternalApi, customerInternalApi);
    }

    @Test
    void expirePaymentAttemptIfDue_WhenPaymentIsMissing_ShouldReturnFalseWithoutEffects() {
        paymentId = UUID.randomUUID();
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.empty());

        boolean expired = paymentAttemptService.expirePaymentAttemptIfDue(paymentId, TEST_NOW);

        assertEquals(false, expired);
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(orderInternalApi, productInternalApi, customerInternalApi);
    }

    @Test
    void expirePaymentAttemptIfDue_ShouldUseEvaluationTimeRatherThanCurrentClock() {
        var createdPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.CREATED);
        paymentId = createdPaymentAttempt.getId();
        ReflectionTestUtils.setField(createdPaymentAttempt, "createdAt", TEST_NOW.minusSeconds(900));
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(createdPaymentAttempt));

        boolean expired = paymentAttemptService.expirePaymentAttemptIfDue(paymentId, TEST_NOW.minusSeconds(1));

        assertEquals(false, expired);
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(clock, orderInternalApi, productInternalApi, customerInternalApi);
    }

    @Test
    void expirePaymentAttemptIfDue_WhenStockCannotBeRestored_ShouldPropagateWithoutSavingOrChangingOrder() {
        var createdPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.CREATED);
        paymentId = createdPaymentAttempt.getId();
        ReflectionTestUtils.setField(createdPaymentAttempt, "createdAt", TEST_NOW.minusSeconds(900));
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(createdPaymentAttempt));
        when(orderInternalApi.getAllOrderItemsFromOrder(orderId))
                .thenReturn(List.of(createOrderItemResponse("SKU-EXPIRATION", 2L)));
        var missingProductFailure = new ProductNotFoundException();
        doThrow(missingProductFailure).when(productInternalApi).increaseProductStock("SKU-EXPIRATION", 2L);

        var actualFailure = assertThrows(ProductNotFoundException.class,
                () -> paymentAttemptService.expirePaymentAttemptIfDue(paymentId, TEST_NOW));

        assertSame(missingProductFailure, actualFailure);
        verify(orderInternalApi, never()).markAsPending(any(UUID.class));
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
    }

    @ParameterizedTest
    @ValueSource(longs = { 900, 901 })
    void startProcessing_WhenPaymentDeadlineHasBeenReached_ShouldRejectBeforeChangingAnything(long paymentAgeInSeconds) {
        var createdPaymentAttempt = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.CREATED);
        paymentId = createdPaymentAttempt.getId();
        ReflectionTestUtils.setField(createdPaymentAttempt, "createdAt", TEST_NOW.minusSeconds(paymentAgeInSeconds));
        when(paymentAttemptRepository.findById(paymentId)).thenReturn(Optional.of(createdPaymentAttempt));

        var invalidPaymentStateException = assertThrows(InvalidPaymentStateException.class,
                () -> paymentAttemptService.startProcessing(orderId, paymentId));

        assertEquals("Expired payment attempts cannot start processing.", invalidPaymentStateException.getMessage());
        assertEquals(PaymentStatus.CREATED, createdPaymentAttempt.getStatus());
        verify(paymentAttemptRepository, never()).save(any(PaymentAttemptEntity.class));
        verifyNoInteractions(orderInternalApi, productInternalApi, customerInternalApi);
    }

    private DataIntegrityViolationException createConstraintFailure(String constraintName) {
        var constraintViolation = new ConstraintViolationException("Duplicate key", new SQLException("Duplicate", "23505"),
                constraintName);
        return new DataIntegrityViolationException("Constraint violation", constraintViolation);
    }

    private void assertExistingPaymentReturnedAfterTransactionFailure(RuntimeException creationFailure) {
        doThrow(creationFailure).doAnswer(this::executePaymentTransaction).when(transactionTemplate).execute(any());
        var committedPayment = createPaymentAttemptWithStatusAndOrder(orderId, PaymentStatus.CREATED);
        when(paymentAttemptRepository.findByIdempotencyKey(DEFAULT_IDEMPOTENCY_KEY)).thenReturn(Optional.of(committedPayment));
        var createPaymentRequest = new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, DEFAULT_IDEMPOTENCY_KEY);

        var recoveredPaymentResponse = paymentAttemptService.createPaymentAttempt(orderId, createPaymentRequest);

        assertEquals(committedPayment.getId(), recoveredPaymentResponse.id());
        assertEquals(orderId, recoveredPaymentResponse.orderId());
        assertEquals(DEFAULT_IDEMPOTENCY_KEY, recoveredPaymentResponse.idempotencyKey());
        verify(transactionTemplate, times(2)).execute(any());
        verify(paymentAttemptRepository).findByIdempotencyKey(DEFAULT_IDEMPOTENCY_KEY);
        verifyNoMoreInteractions(paymentAttemptRepository);
        verifyNoInteractions(orderInternalApi, customerInternalApi, productInternalApi);
    }

    private Object executePaymentTransaction(InvocationOnMock invocation) {
        TransactionCallback<?> transactionCallback = invocation.getArgument(0);
        return transactionCallback.doInTransaction(new SimpleTransactionStatus());
    }

    private void mockPaymentRepositorySave() {
        when(paymentAttemptRepository.save(any(PaymentAttemptEntity.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void mockCustomer(CustomerStatus status) {
        when(customerInternalApi.getCustomer(customerId)).thenReturn(CustomerResponse.builder()
                .id(customerId).name("Test Customer").email("customer@example.com").status(status).build());
    }

    private OrderItemResponse createOrderItemResponse(String sku, long quantity) {
        return OrderItemResponse.builder().id(UUID.randomUUID()).orderId(orderId)
                .sku(sku).quantity(quantity).unitPrice(new BigDecimal("10.00"))
                .lineTotal(new BigDecimal("10.00").multiply(BigDecimal.valueOf(quantity))).build();
    }

    private static PaymentAttemptEntity createPaymentAttemptWithStatusAndOrder(UUID orderId,
            PaymentStatus status) {
        var paymentAttempt = PaymentAttemptEntity.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .idempotencyKey(DEFAULT_IDEMPOTENCY_KEY)
                .status(status)
                .build();
        ReflectionTestUtils.setField(paymentAttempt, "createdAt", TEST_NOW);
        return paymentAttempt;
    }

    private PaymentAttemptEntity createSucceededPaymentAttempt(String providerReference) {
        return PaymentAttemptEntity.builder().id(UUID.randomUUID()).orderId(orderId)
                .idempotencyKey(DEFAULT_IDEMPOTENCY_KEY).status(PaymentStatus.SUCCEEDED)
                .providerRef(providerReference).build();
    }

}
