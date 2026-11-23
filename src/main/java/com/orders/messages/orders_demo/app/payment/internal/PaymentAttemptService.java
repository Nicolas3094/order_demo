package com.orders.messages.orders_demo.app.payment.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.orders.messages.orders_demo.app.customer.api.CustomerInternalApi;
import com.orders.messages.orders_demo.app.customer.api.CustomerResponse;
import com.orders.messages.orders_demo.app.customer.internal.CustomerStatus;
import com.orders.messages.orders_demo.app.order.api.OrderData;
import com.orders.messages.orders_demo.app.order.api.OrderInternalApi;
import com.orders.messages.orders_demo.app.order.api.OrderItemResponse;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderNotFoundException;
import com.orders.messages.orders_demo.app.payment.api.CreatePaymentAttemptRequest;
import com.orders.messages.orders_demo.app.payment.api.PaymentAttemptResponse;
import com.orders.messages.orders_demo.app.payment.internal.exceptions.InvalidPaymentStateException;
import com.orders.messages.orders_demo.app.payment.internal.exceptions.PaymentNotFoundException;
import com.orders.messages.orders_demo.app.product.api.ProductInternalApi;

import jakarta.transaction.Transactional;

@Service
public class PaymentAttemptService {

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final OrderInternalApi orderInternalApi;
    private final CustomerInternalApi customerInternalApi;
    private final ProductInternalApi productInternalApi;

    public PaymentAttemptService(PaymentAttemptRepository paymentAttemptRepository,
            OrderInternalApi orderInternalApi, CustomerInternalApi customerInternalApi,
            ProductInternalApi productInternalApi) {
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.orderInternalApi = orderInternalApi;
        this.customerInternalApi = customerInternalApi;
        this.productInternalApi = productInternalApi;
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

        Optional<PaymentAttemptEntity> paymentOpt = paymentAttemptRepository
                .findByIdempotencyKey(paymentRequest.idempotencyKey());

        if (paymentOpt.isPresent()) {

            boolean comesFromSameOrder = paymentOpt.get().getOrderId().equals(orderId);

            if (comesFromSameOrder) {
                return PaymentAttemptMapper.toResponse(paymentOpt.get());
            }

            throw new InvalidPaymentStateException("Requested order ID is not equal to payment's order.");
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
    @Transactional
    public PaymentAttemptResponse markAsSucceeded(UUID orderId, UUID paymentId, String providerRef) {

        PaymentAttemptEntity payment = findPaymentAttempt(orderId, paymentId);

        if (PaymentStatus.SUCCEEDED.equals(payment.getStatus())) {

            if (!providerRef.equals(payment.getProviderRef())) {
                throw new InvalidPaymentStateException("Successful payment has a different provider reference.");
            }

            return PaymentAttemptMapper.toResponse(payment);
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
    @Transactional
    public PaymentAttemptResponse markAsFailed(UUID orderId, UUID paymentId, Integer code, String errorMessage) {
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

        List<OrderItemResponse> orderItems = orderInternalApi.getAllOrderItemsFromOrder(orderId);

        for (var orderItem : orderItems) {
            Long requestedQuantity = orderItem.quantity();

            productInternalApi.increaseProductStock(orderItem.sku(), requestedQuantity);
        }
    }

    private void decreaseStock(UUID orderId) {

        List<OrderItemResponse> orderItems = orderInternalApi.getAllOrderItemsFromOrder(orderId);

        for (var orderItem : orderItems) {
            Long requestedQuantity = orderItem.quantity();

            productInternalApi.decreaseProductStock(orderItem.sku(), requestedQuantity);
        }
    }
}
