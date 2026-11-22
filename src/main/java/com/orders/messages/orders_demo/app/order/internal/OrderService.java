package com.orders.messages.orders_demo.app.order.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orders.messages.orders_demo.app.customer.api.CustomerInternalApi;
import com.orders.messages.orders_demo.app.customer.api.CustomerResponse;
import com.orders.messages.orders_demo.app.customer.internal.CustomerStatus;
import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerNotFoundException;
import com.orders.messages.orders_demo.app.order.api.CreateOrderRequest;
import com.orders.messages.orders_demo.app.order.api.OrderResponse;
import com.orders.messages.orders_demo.app.order.internal.exceptions.InvalidOrderStateException;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderNotFoundException;
import com.orders.messages.orders_demo.app.product.internal.exceptions.ProductNotFoundException;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerInternalApi customerInternalApi;
    private final JdbcTemplate jdbcTemplate;

    public OrderService(OrderRepository repository,
            CustomerInternalApi customerInternalApi,
            JdbcTemplate jdbcTemplate) {
        this.orderRepository = repository;
        this.customerInternalApi = customerInternalApi;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Retrieves all orders.
     *
     * @return a list containing all persisted orders.
     */
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(OrderMapper::toResponse)
                .toList();
    }

    /**
     * Retrieves an order by its identifier.
     *
     * @param id the order identifier.
     * @return the requested order.
     * @throws OrderNotFoundException if the order does not exist.
     */
    public OrderResponse getOrder(UUID id) {
        return orderRepository.findById(id)
                .map(OrderMapper::toResponse)
                .orElseThrow(OrderNotFoundException::new);
    }

    /**
     * Returns the customer's open order or creates one when none exists.
     *
     * @throws CustomerNotFoundException  if the customer does not exist.
     * @throws InvalidOrderStateException if the open order uses another currency.
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        return createOrReuseOrder(request).order();
    }

    @Transactional
    public OrderCreationResult createOrReuseOrder(CreateOrderRequest request) {
        CustomerResponse customerResponse = customerInternalApi.getCustomer(request.customerId());

        if (CustomerStatus.BLOCKED.equals(customerResponse.status())) {
            throw new InvalidOrderStateException("Blocked customers cannot create or reuse orders.");
        }

        UUID customerId = customerResponse.id();

        lockOrderCreation(customerId);

        Optional<OrderEntity> existingOrder = orderRepository.findByCustomerIdAndStatusIn(
                customerId, List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_IN_PROGRESS));

        if (existingOrder.isPresent()) {

            OrderEntity order = existingOrder.get();

            if (order.getCurrency() != request.currency()) {
                throw new InvalidOrderStateException("The open order uses a different currency.");
            }

            return new OrderCreationResult(OrderMapper.toResponse(order), false);
        }

        OrderEntity order = orderRepository.save(OrderMapper.toEntity(request, customerId));

        return new OrderCreationResult(OrderMapper.toResponse(order), true);
    }

    /**
     * Cancels an order and restores the stock of all associated products.
     *
     * @param id the order identifier.
     * @return the updated order.
     * @throws OrderNotFoundException   if the order does not exist.
     * @throws ProductNotFoundException if any associated product cannot be found.
     */
    @Transactional
    public OrderResponse cancelOrder(UUID id) {
        return validateOrderAnAct(id, OrderEntity::cancelOrder);
    }

    /**
     * Expires an order and restores the stock of all associated products.
     *
     * @param id the order identifier.
     * @return the updated order.
     * @throws OrderNotFoundException   if the order does not exist.
     * @throws ProductNotFoundException if any associated product cannot be found.
     */
    @Transactional
    public OrderResponse expireOrder(UUID id) {
        return validateOrderAnAct(id, OrderEntity::expire);
    }

    /**
     * Refunds an order and restores the stock of all associated products.
     *
     * @param id the order identifier.
     * @return the updated order.
     * @throws OrderNotFoundException   if the order does not exist.
     * @throws ProductNotFoundException if any associated product cannot be found.
     */
    @Transactional
    public OrderResponse refundOrder(UUID id) {
        return validateOrderAnAct(id, OrderEntity::refund);
    }

    /**
     * Sets the status as "payment in progress".
     *
     * @param id the order identifier.
     * @return the updated order.
     * @throws OrderNotFoundException if the order does not exist.
     */
    @Transactional
    public OrderResponse paymentInProgress(UUID id) {
        OrderEntity order = orderRepository.findById(id).orElseThrow(OrderNotFoundException::new);

        order.paymentInProgress();

        return OrderMapper.toResponse(orderRepository.save(order));
    }

    /**
     * Applies a state transition to an validated order.
     *
     * @param id     the order identifier.
     * @param action the state transition to apply.
     * @return the updated order.
     * @throws OrderNotFoundException if the order does not exist.
     */
    private OrderResponse validateOrderAnAct(UUID id, Consumer<OrderEntity> action) {
        OrderEntity order = orderRepository.findById(id).orElseThrow(OrderNotFoundException::new);

        action.accept(order);

        return OrderMapper.toResponse(orderRepository.save(order));

    }

    private void lockOrderCreation(UUID customerId) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, "customer-open-order:" + customerId);
                statement.execute();
            }
            return null;
        });
    }

    public record OrderCreationResult(OrderResponse order, boolean created) {
    }
}
