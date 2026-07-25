package com.orders.messages.orders_demo.services;

import java.util.Optional;
import java.util.UUID;

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

    public PaymentAttempt getPaymentAttempt(UUID orderId, UUID paymentId) {
        PaymentAttempt paymentAttempt = findPaymentAttempt(orderId, paymentId);

        return paymentAttempt;
    }

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

    @Transactional
    public PaymentAttempt startProcessing(UUID orderId, UUID paymentId) {
        PaymentAttempt paymentAttempt = findPaymentAttempt(orderId, paymentId);
        Order order = paymentAttempt.getOrder();

        if (!order.canAcceptPayments()) {
            throw new InvalidPaymentStateException("Only pending orders can be processed.");
        }

        paymentAttempt.startProcessing();

        return paymentAttemptRepository.save(paymentAttempt);
    }

    @Transactional
    public PaymentAttempt markAsSucceeded(UUID orderId, UUID paymentId, String providerRef) {
        PaymentAttempt paymentAttempt = findPaymentAttempt(orderId, paymentId);
        Order order = paymentAttempt.getOrder();

        if (!order.canAcceptPayments()) {
            throw new InvalidPaymentStateException("Only pending orders can be marked as paid.");
        }

        paymentAttempt.markAsSucceeded(providerRef);

        order.markAsPaid();

        orderRepository.save(order);

        return paymentAttemptRepository.save(paymentAttempt);
    }

    @Transactional
    public PaymentAttempt markAsFailed(UUID orderId, UUID paymentId, Integer code, String errorMessage) {
        PaymentAttempt paymentAttempt = findPaymentAttempt(orderId, paymentId);

        paymentAttempt.markAsFailed(code, errorMessage);

        return paymentAttemptRepository.save(paymentAttempt);
    }

    @Transactional
    public PaymentAttempt markAsCancelled(UUID orderId, UUID paymentId) {
        PaymentAttempt paymentAttempt = findPaymentAttempt(orderId, paymentId);

        paymentAttempt.cancel();

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
