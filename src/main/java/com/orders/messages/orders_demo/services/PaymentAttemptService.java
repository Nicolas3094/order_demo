package com.orders.messages.orders_demo.services;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.orders.messages.orders_demo.dtos.request.CreatePaymentAttemptRequest;
import com.orders.messages.orders_demo.entity.Order;
import com.orders.messages.orders_demo.entity.PaymentAttempt;
import com.orders.messages.orders_demo.exceptions.orders.OrderNotFoundException;
import com.orders.messages.orders_demo.exceptions.payment.InvalidPaymentStateException;
import com.orders.messages.orders_demo.exceptions.payment.PaymentNotFoundException;
import com.orders.messages.orders_demo.mappers.PaymentAttemptMapper;
import com.orders.messages.orders_demo.repositories.OrderRepository;
import com.orders.messages.orders_demo.repositories.PaymentAttemptRepository;

import jakarta.transaction.Transactional;

@Service
public class PaymentAttemptService {

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final OrderRepository orderRepository;

    public PaymentAttemptService(OrderRepository orderRepository, PaymentAttemptRepository paymentAttemptRepository) {
        this.orderRepository = orderRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
    }

    /**
     * Retrieves all payment attempts associated with the specified order.
     *
     * @param orderId the order identifier.
     * @return a list containing all payment attempts for the order.
     * @throws OrderNotFoundException if the order does not exist.
     */
    public List<PaymentAttempt> getAllPayments(UUID orderId) {
        return paymentAttemptRepository.findByOrderId(findOrder(orderId).getId());
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
    public PaymentAttempt getPaymentAttempt(UUID orderId, UUID paymentId) {
        return findPaymentAttempt(orderId, paymentId);
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
    public PaymentAttempt createPaymentAttempt(UUID orderId, CreatePaymentAttemptRequest paymentRequest) {
        Order order = findOrder(orderId);

        if (!order.canAcceptPayments()) {
            throw new InvalidPaymentStateException("Only pending orders can receive payment attempts.");
        }

        Optional<PaymentAttempt> paymentOpt = paymentAttemptRepository
                .findByIdempotencyKey(paymentRequest.idempotencyKey());

        if (paymentOpt.isPresent()) {
            return paymentOpt.get();
        }

        return paymentAttemptRepository.save(PaymentAttemptMapper.toEntity(paymentRequest, order));
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
    public PaymentAttempt startProcessing(UUID orderId, UUID paymentId) {
        return updatePaymentAttemptState(orderId, paymentId, payment -> {
            Order order = payment.getOrder();

            if (!order.canAcceptPayments()) {
                throw new InvalidPaymentStateException("Only pending orders can be processed.");
            }

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
    public PaymentAttempt markAsSucceeded(UUID orderId, UUID paymentId, String providerRef) {
        return updatePaymentAttemptState(orderId, paymentId, payment -> {
            Order order = payment.getOrder();

            if (!order.canAcceptPayments()) {
                throw new InvalidPaymentStateException("Only pending orders can be marked as paid.");
            }

            payment.markAsSucceeded(providerRef);

            order.markAsPaid();

            orderRepository.save(order);
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
    public PaymentAttempt markAsFailed(UUID orderId, UUID paymentId, Integer code, String errorMessage) {
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
    public PaymentAttempt markAsCancelled(UUID orderId, UUID paymentId) {
        return updatePaymentAttemptState(orderId, paymentId, PaymentAttempt::cancel);
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
    private PaymentAttempt updatePaymentAttemptState(UUID orderId, UUID paymentId, Consumer<PaymentAttempt> action) {
        PaymentAttempt paymentAttempt = findPaymentAttempt(orderId, paymentId);

        action.accept(paymentAttempt);

        return paymentAttemptRepository.save(paymentAttempt);
    }

    /**
     * Finds the Payment if exits, otherwise throws an
     * {@link PaymentNotFoundException}.
     * 
     * @param orderId   The Order ID.
     * @param paymentId The PaymentAttempt ID.
     * @return A complete Payment object.
     */
    private PaymentAttempt findPaymentAttempt(UUID orderId, UUID paymentId) {
        PaymentAttempt paymentAttempt = paymentAttemptRepository.findById(paymentId)
                .orElseThrow(PaymentNotFoundException::new);

        if (!orderId.equals(paymentAttempt.getOrder().getId())) {
            throw new PaymentNotFoundException();
        }

        return paymentAttempt;
    }

    /**
     * Finds the Order if exits, otherwise throws an
     * {@link OrderNotFoundException}.
     * 
     * @param orderId The Order ID.
     * @return A complete Order object.
     */
    private Order findOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(OrderNotFoundException::new);
    }

}
