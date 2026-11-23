package com.orders.messages.orders_demo.app.payment.internal;

import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.hibernate.exception.ConstraintViolationException;
import jakarta.persistence.OptimisticLockException;

import com.orders.messages.orders_demo.app.customer.api.CustomerInternalApi;
import com.orders.messages.orders_demo.app.customer.api.CustomerResponse;
import com.orders.messages.orders_demo.app.customer.internal.CustomerStatus;
import com.orders.messages.orders_demo.app.order.api.OrderData;
import com.orders.messages.orders_demo.app.order.api.OrderInternalApi;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderNotFoundException;
import com.orders.messages.orders_demo.app.order.internal.exceptions.InvalidOrderStateException;
import com.orders.messages.orders_demo.app.payment.api.CreatePaymentAttemptRequest;
import com.orders.messages.orders_demo.app.payment.api.PaymentAttemptResponse;
import com.orders.messages.orders_demo.app.payment.internal.exceptions.InvalidPaymentStateException;
import com.orders.messages.orders_demo.app.payment.internal.exceptions.PaymentNotFoundException;
import com.orders.messages.orders_demo.app.product.api.ProductInternalApi;
import com.orders.messages.orders_demo.app.product.internal.exceptions.InsufficientStockException;

import jakarta.transaction.Transactional;

@Service
public class PaymentAttemptService {

    private static final Duration PAYMENT_LIFETIME = Duration.ofMinutes(15);

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final OrderInternalApi orderInternalApi;
    private final CustomerInternalApi customerInternalApi;
    private final ProductInternalApi productInternalApi;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public PaymentAttemptService(PaymentAttemptRepository paymentAttemptRepository,
            OrderInternalApi orderInternalApi, CustomerInternalApi customerInternalApi,
            ProductInternalApi productInternalApi, TransactionTemplate transactionTemplate, Clock clock) {
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.orderInternalApi = orderInternalApi;
        this.customerInternalApi = customerInternalApi;
        this.productInternalApi = productInternalApi;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    /**
     * Retrieves all payment attempts associated with the specified order.
     *
     * @param orderId the order identifier.
     * @return a list containing all payment attempts for the order.
     * @throws OrderNotFoundException if the order does not exist.
     */
    public List<PaymentAttemptResponse> getAllPayments(UUID orderId) {
        orderInternalApi.validateOrderExists(orderId);

        return paymentAttemptRepository.findByOrderId(orderId).stream()
                .map(PaymentAttemptMapper::toResponse)
                .toList();
    }

    /**
     * Retrieves a payment attempt by its identifier and verifies that it belongs to
     * the specified order.
     *
     * @param orderId   the order identifier.
     * @param paymentId the payment attempt identifier.
     * @return the requested payment attempt.
     * @throws PaymentNotFoundException if the payment attempt does not exist or
     *                                  does not belong to the specified order.
     */
    public PaymentAttemptResponse getPaymentAttempt(UUID orderId, UUID paymentId) {
        return PaymentAttemptMapper.toResponse(findPaymentAttempt(orderId, paymentId));
    }

    /**
     * Creates a new payment attempt for the specified order.
     *
     * <p>
     * If a payment attempt with the same idempotency key already exists, the
     * existing payment attempt is returned instead of creating a new one.
     * </p>
     *
     * @param orderId        the order identifier.
     * @param paymentRequest the payment attempt creation request.
     * @return the newly created payment attempt, or the existing one with the same
     *         idempotency key.
     * @throws OrderNotFoundException       if the order does not exist.
     * @throws InvalidPaymentStateException if the order can no longer receive
     *                                      payment attempts.
     */
    public PaymentAttemptResponse createPaymentAttempt(UUID orderId, CreatePaymentAttemptRequest paymentRequest) {
        try {
            return transactionTemplate.execute(status -> createPaymentInTransaction(orderId, paymentRequest));
        } catch (RuntimeException creationFailure) {
            // A caller-owned transaction cannot be recovered here: it must roll back as a
            // whole.
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    || !canRecoverPaymentCreation(creationFailure)) {
                throw creationFailure;
            }

            // execute has already completed the rollback; this lookup starts a valid
            // transaction.
            return transactionTemplate.execute(status -> paymentAttemptRepository
                    .findByIdempotencyKey(paymentRequest.idempotencyKey())
                    .map(payment -> responseForExistingPayment(orderId, payment))
                    .orElseThrow(() -> creationFailure));
        }
    }

    private PaymentAttemptResponse createPaymentInTransaction(UUID orderId,
            CreatePaymentAttemptRequest paymentRequest) {
        Optional<PaymentAttemptEntity> paymentOpt = paymentAttemptRepository
                .findByIdempotencyKey(paymentRequest.idempotencyKey());

        if (paymentOpt.isPresent()) {
            return responseForExistingPayment(orderId, paymentOpt.get());
        }

        OrderData orderData = orderInternalApi.getOrderDataForPayment(orderId);

        validateCustomerCanStartPayment(orderData.customerId());

        decreaseStock(orderId);

        orderInternalApi.markAsPaymentInProgress(orderId);

        return PaymentAttemptMapper.toResponse(paymentAttemptRepository
                .save(PaymentAttemptMapper.toEntity(
                        paymentRequest,
                        orderData.amountTotal(),
                        orderId)));
    }

    private PaymentAttemptResponse responseForExistingPayment(UUID orderId, PaymentAttemptEntity payment) {
        if (!payment.getOrderId().equals(orderId)) {
            throw new InvalidPaymentStateException("Requested order ID is not equal to payment's order.");
        }
        return PaymentAttemptMapper.toResponse(payment);
    }

    private boolean canRecoverPaymentCreation(RuntimeException creationFailure) {
        if (creationFailure instanceof DataIntegrityViolationException) {
            Throwable cause = creationFailure;
            while (cause != null) {
                if (cause instanceof ConstraintViolationException constraintViolation
                        && "uk_idempotency_key".equals(constraintViolation.getConstraintName())) {
                    return true;
                }
                cause = cause.getCause();
            }
            return false;
        }
        return creationFailure instanceof OptimisticLockingFailureException
                || creationFailure instanceof OptimisticLockException
                || creationFailure instanceof InsufficientStockException
                || (creationFailure instanceof InvalidOrderStateException
                        && !(creationFailure instanceof OrderNotFoundException));
    }

    /**
     * Marks a payment attempt as processing.
     *
     * @param orderId   the order identifier.
     * @param paymentId the payment attempt identifier.
     * @return the updated payment attempt.
     * @throws PaymentNotFoundException     if the payment attempt does not exist or
     *                                      does not belong to the specified order.
     * @throws InvalidPaymentStateException if the payment attempt cannot transition
     *                                      to the processing state.
     */
    @Transactional
    public PaymentAttemptResponse startProcessing(UUID orderId, UUID paymentId) {
        PaymentAttemptEntity paymentAttempt = findPaymentAttempt(orderId, paymentId);

        if (paymentAttempt.getStatus() == PaymentStatus.CREATED
                && hasReachedPaymentDeadline(paymentAttempt, clock.instant())) {
            throw new InvalidPaymentStateException("Expired payment attempts cannot start processing.");
        }

        orderInternalApi.validateCanReceivePayment(orderId);
        UUID customerId = orderInternalApi.getOrderById(orderId).customerId();
        validateCustomerCanStartPayment(customerId);

        paymentAttempt.startProcessing();

        return PaymentAttemptMapper.toResponse(paymentAttemptRepository.save(paymentAttempt));
    }

    /**
     * Marks a payment attempt as successful and updates the associated order as
     * paid.
     *
     * @param orderId     the order identifier.
     * @param paymentId   the payment attempt identifier.
     * @param providerRef the payment provider reference.
     * @return the updated payment attempt.
     * @throws PaymentNotFoundException     if the payment attempt does not exist or
     *                                      does not belong to the specified order.
     * @throws InvalidPaymentStateException if the payment attempt cannot transition
     *                                      to the succeeded state.
     */
    public PaymentAttemptResponse markAsSucceeded(UUID orderId, UUID paymentId, String providerRef) {
        return executePaymentNotification(orderId, paymentId,
                () -> markAsSucceededInTransaction(orderId, paymentId, providerRef),
                payment -> payment.getStatus() == PaymentStatus.SUCCEEDED
                        && Objects.equals(providerRef, payment.getProviderRef()));
    }

    private PaymentAttemptResponse markAsSucceededInTransaction(UUID orderId, UUID paymentId, String providerRef) {
        PaymentAttemptEntity payment = findPaymentAttempt(orderId, paymentId);

        if (PaymentStatus.SUCCEEDED.equals(payment.getStatus())) {

            if (providerRef.equals(payment.getProviderRef())) {
                return PaymentAttemptMapper.toResponse(payment);
            }

            throw new InvalidPaymentStateException("Successful payment has a different provider reference.");
        }

        if (paymentAttemptRepository.existsByProviderAndProviderRef(payment.getProvider(), providerRef)) {
            throw new InvalidPaymentStateException("A successful payment has the same provider reference.");
        }

        payment.markAsSucceeded(providerRef);

        orderInternalApi.markAsPaid(orderId);

        return PaymentAttemptMapper.toResponse(paymentAttemptRepository.save(payment));
    }

    /**
     * Marks a payment attempt as failed.
     *
     * @param orderId      the order identifier.
     * @param paymentId    the payment attempt identifier.
     * @param code         the failure code reported by the payment provider.
     * @param errorMessage the failure description reported by the payment provider.
     * @return the updated payment attempt.
     * @throws PaymentNotFoundException     if the payment attempt does not exist or
     *                                      does not belong to the specified order.
     * @throws InvalidPaymentStateException if the payment attempt cannot transition
     *                                      to the failed state.
     */
    public PaymentAttemptResponse markAsFailed(UUID orderId, UUID paymentId, Integer code, String errorMessage) {
        return executePaymentNotification(orderId, paymentId,
                () -> markAsFailedInTransaction(orderId, paymentId, code, errorMessage),
                payment -> payment.getStatus() == PaymentStatus.FAILED
                        && Objects.equals(code, payment.getFailureCode())
                        && Objects.equals(errorMessage, payment.getFailureMessage()));
    }

    private PaymentAttemptResponse markAsFailedInTransaction(UUID orderId, UUID paymentId, Integer code,
            String errorMessage) {
        PaymentAttemptEntity payment = findPaymentAttempt(orderId, paymentId);

        if (PaymentStatus.FAILED.equals(payment.getStatus())) {

            if (!code.equals(payment.getFailureCode()) || !errorMessage.equals(payment.getFailureMessage())) {
                throw new InvalidPaymentStateException("Failed payment has different failure details.");
            }

            return PaymentAttemptMapper.toResponse(payment);
        }

        payment.markAsFailed(code, errorMessage);

        orderInternalApi.markAsPending(orderId);

        increaseStock(orderId);

        return PaymentAttemptMapper.toResponse(paymentAttemptRepository.save(payment));
    }

    private PaymentAttemptResponse executePaymentNotification(UUID orderId, UUID paymentId,
            Supplier<PaymentAttemptResponse> notification, Predicate<PaymentAttemptEntity> matchesNotification) {
        try {
            return transactionTemplate.execute(status -> notification.get());
        } catch (RuntimeException notificationFailure) {
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    || !canRecoverPaymentNotification(notificationFailure)) {
                throw notificationFailure;
            }

            // Re-read only after rollback. Recognizing the result must not repeat order or
            // stock changes.
            return transactionTemplate.execute(status -> paymentAttemptRepository.findById(paymentId)
                    .filter(payment -> orderId.equals(payment.getOrderId()))
                    .filter(matchesNotification)
                    .map(PaymentAttemptMapper::toResponse)
                    .orElseThrow(() -> notificationFailure));
        }
    }

    private boolean canRecoverPaymentNotification(RuntimeException notificationFailure) {
        return notificationFailure instanceof OptimisticLockingFailureException
                || notificationFailure instanceof OptimisticLockException
                || (notificationFailure instanceof InvalidPaymentStateException
                        && !(notificationFailure instanceof PaymentNotFoundException))
                || (notificationFailure instanceof InvalidOrderStateException
                        && !(notificationFailure instanceof OrderNotFoundException));
    }

    /**
     * Expires an unsent CREATED attempt once its 15-minute reservation ends.
     *
     * @return true when this call expires the attempt; false when no expiration
     *         applies.
     */
    @Transactional
    public boolean expirePaymentAttemptIfDue(UUID paymentId, Instant evaluationTime) {
        Objects.requireNonNull(evaluationTime, "Evaluation time must not be null.");
        Optional<PaymentAttemptEntity> paymentOpt = paymentAttemptRepository.findById(paymentId);
        if (paymentOpt.isEmpty()) {
            return false;
        }

        PaymentAttemptEntity payment = paymentOpt.get();
        if (payment.getStatus() != PaymentStatus.CREATED || payment.getProviderRef() != null
                || !hasReachedPaymentDeadline(payment, evaluationTime)) {
            return false;
        }

        payment.cancel();
        increaseStock(payment.getOrderId());
        orderInternalApi.markAsPending(payment.getOrderId());
        paymentAttemptRepository.save(payment);
        return true;
    }

    private boolean hasReachedPaymentDeadline(PaymentAttemptEntity payment, Instant evaluationTime) {
        return !evaluationTime.isBefore(payment.getCreatedAt().plus(PAYMENT_LIFETIME));
    }

    /**
     * Cancels a payment attempt.
     *
     * @param orderId   the order identifier.
     * @param paymentId the payment attempt identifier.
     * @return the updated payment attempt.
     * @throws PaymentNotFoundException     if the payment attempt does not exist or
     *                                      does not belong to the specified order.
     * @throws InvalidPaymentStateException if the payment attempt cannot transition
     *                                      to the cancelled state.
     */
    @Transactional
    public PaymentAttemptResponse markAsCancelled(UUID orderId, UUID paymentId) {
        PaymentAttemptEntity paymentAttempt = findPaymentAttempt(orderId, paymentId);

        paymentAttempt.cancel();

        increaseStock(orderId);

        orderInternalApi.markAsPending(orderId);

        return PaymentAttemptMapper.toResponse(paymentAttemptRepository.save(paymentAttempt));
    }

    /** Validates the customer independently of the order's payment state. */
    private void validateCustomerCanStartPayment(UUID customerId) {
        CustomerResponse customer = customerInternalApi.getCustomer(customerId);

        if (CustomerStatus.BLOCKED.equals(customer.status())) {
            throw new InvalidPaymentStateException("Blocked customers cannot start payments.");
        }
    }

    /**
     * Finds the Payment if exits, otherwise throws an
     * {@link PaymentNotFoundException}.
     * 
     * @param orderId   The Order ID.
     * @param paymentId The PaymentAttempt ID.
     * @return A complete Payment object.
     */
    private PaymentAttemptEntity findPaymentAttempt(UUID orderId, UUID paymentId) {
        PaymentAttemptEntity paymentAttempt = paymentAttemptRepository.findById(paymentId)
                .orElseThrow(PaymentNotFoundException::new);

        if (!orderId.equals(paymentAttempt.getOrderId())) {
            throw new PaymentNotFoundException();
        }

        return paymentAttempt;
    }

    private void increaseStock(UUID orderId) {
        balanceStock(orderId, productInternalApi::increaseProductStock);
    }

    private void decreaseStock(UUID orderId) {
        balanceStock(orderId, productInternalApi::decreaseProductStock);
    }

    private void balanceStock(UUID orderId, BiConsumer<String, Long> action) {
        for (var orderItem : orderInternalApi.getAllOrderItemsFromOrder(orderId)) {
            action.accept(orderItem.sku(), orderItem.quantity());
        }
    }
}
