package com.orders.messages.orders_demo.app.payment.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.orders.messages.orders_demo.app.order.api.OrderData;
import com.orders.messages.orders_demo.app.order.api.OrderInternalApi;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderNotFoundException;
import com.orders.messages.orders_demo.app.payment.api.CreatePaymentAttemptRequest;
import com.orders.messages.orders_demo.app.payment.api.PaymentAttemptResponse;
import com.orders.messages.orders_demo.app.payment.internal.exceptions.InvalidPaymentStateException;
import com.orders.messages.orders_demo.app.payment.internal.exceptions.PaymentNotFoundException;

import jakarta.transaction.Transactional;

@Service
public class PaymentAttemptService {

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final OrderInternalApi orderInternalApi;

    public PaymentAttemptService(PaymentAttemptRepository paymentAttemptRepository,
            OrderInternalApi orderInternalApi) {
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.orderInternalApi = orderInternalApi;
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
    @Transactional
    public PaymentAttemptResponse createPaymentAttempt(UUID orderId, CreatePaymentAttemptRequest paymentRequest) {
        OrderData orderData = orderInternalApi.getOrderDataForPayment(orderId);

        Optional<PaymentAttemptEntity> paymentOpt = paymentAttemptRepository
                .findByIdempotencyKey(paymentRequest.idempotencyKey());

        if (paymentOpt.isPresent()) {
            return PaymentAttemptMapper.toResponse(paymentOpt.get());
        }

        return PaymentAttemptMapper.toResponse(paymentAttemptRepository
                .save(PaymentAttemptMapper.toEntity(
                        paymentRequest,
                        orderData.amountTotal(),
                        orderId)));
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
        return updatePaymentAttemptState(orderId, paymentId, payment -> {
            orderInternalApi.validateCanReceivePayment(orderId);

            payment.startProcessing();
        });
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
    @Transactional
    public PaymentAttemptResponse markAsSucceeded(UUID orderId, UUID paymentId, String providerRef) {
        return updatePaymentAttemptState(orderId, paymentId, payment -> {
            payment.markAsSucceeded(providerRef);

            orderInternalApi.markAsPaid(orderId);
        });
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
    @Transactional
    public PaymentAttemptResponse markAsFailed(UUID orderId, UUID paymentId, Integer code, String errorMessage) {
        return updatePaymentAttemptState(orderId, paymentId, payment -> payment.markAsFailed(code, errorMessage));
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
        return updatePaymentAttemptState(orderId, paymentId, PaymentAttemptEntity::cancel);
    }

    /**
     * Applies the given state transition to a payment attempt and persists the
     * changes.
     *
     * @param orderId   the order identifier.
     * @param paymentId the payment attempt identifier.
     * @param action    the state transition to apply.
     * @return the updated payment attempt.
     * @throws PaymentNotFoundException if the payment attempt does not exist or
     *                                  does not belong to the specified order.
     */
    private PaymentAttemptResponse updatePaymentAttemptState(UUID orderId, UUID paymentId,
            Consumer<PaymentAttemptEntity> action) {
        PaymentAttemptEntity paymentAttempt = findPaymentAttempt(orderId, paymentId);

        action.accept(paymentAttempt);

        return PaymentAttemptMapper.toResponse(paymentAttemptRepository.save(paymentAttempt));
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

}
