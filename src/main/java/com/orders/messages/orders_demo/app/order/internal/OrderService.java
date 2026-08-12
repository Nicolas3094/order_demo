package com.orders.messages.orders_demo.app.order.internal;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.orders.messages.orders_demo.app.customer.api.CustomerInternalApi;
import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerNotFoundException;
import com.orders.messages.orders_demo.app.order.api.CreateOrderRequest;
import com.orders.messages.orders_demo.app.order.api.OrderResponse;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderNotFoundException;
import com.orders.messages.orders_demo.app.product.api.ProductInternalApi;
import com.orders.messages.orders_demo.app.product.internal.exceptions.ProductNotFoundException;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerInternalApi customerInternalApi;
    private final ProductInternalApi productInternalApi;

    public OrderService(OrderRepository repository,
            CustomerInternalApi customerInternalApi,
            ProductInternalApi productInternalApi) {
        this.orderRepository = repository;
        this.customerInternalApi = customerInternalApi;
        this.productInternalApi = productInternalApi;
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
     * Creates a new order for the specified customer.
     *
     * @param createOrderRequest the information required to create the order.
     * @return the persisted order.
     * @throws CustomerNotFoundException if the customer does not exist.
     */
    public OrderResponse createOrder(CreateOrderRequest createOrderRequest) {
        UUID customerId = customerInternalApi.getCustomer(createOrderRequest.customerId()).id();

        OrderEntity order = OrderMapper.toEntity(createOrderRequest, customerId);

        return OrderMapper.toResponse(orderRepository.save(order));
    }

    /**
     * Cancels an order and restores the stock of all associated products.
     *
     * @param id the order identifier.
     * @return the updated order.
     * @throws OrderNotFoundException   if the order does not exist.
     * @throws ProductNotFoundException if any associated product cannot be found.
     */
    public OrderResponse cancelOrder(UUID id) {
        return restoreProductsStock(id, OrderEntity::cancelOrder);
    }

    /**
     * Expires an order and restores the stock of all associated products.
     *
     * @param id the order identifier.
     * @return the updated order.
     * @throws OrderNotFoundException   if the order does not exist.
     * @throws ProductNotFoundException if any associated product cannot be found.
     */
    public OrderResponse expireOrder(UUID id) {
        return restoreProductsStock(id, OrderEntity::expire);
    }

    /**
     * Refunds an order and restores the stock of all associated products.
     *
     * @param id the order identifier.
     * @return the updated order.
     * @throws OrderNotFoundException   if the order does not exist.
     * @throws ProductNotFoundException if any associated product cannot be found.
     */
    public OrderResponse refundOrder(UUID id) {
        return restoreProductsStock(id, OrderEntity::refund);
    }

    /**
     * Applies a state transition to an order and restores the stock of all
     * associated products.
     *
     * <p>
     * This method is shared by order operations that return reserved inventory to
     * stock, such as cancellation, expiration, and refund.
     * </p>
     *
     * @param id     the order identifier.
     * @param action the state transition to apply.
     * @return the updated order.
     * @throws OrderNotFoundException   if the order does not exist.
     * @throws ProductNotFoundException if any associated product cannot be found.
     */
    private OrderResponse restoreProductsStock(UUID id, Consumer<OrderEntity> action) {
        OrderEntity order = orderRepository.findById(id).orElseThrow(OrderNotFoundException::new);

        action.accept(order);

        // TODO: Optimize product loading using findBySkuIn(...) and saveAll() to avoid
        // N+1 queries for large orders.
        for (OrderItemEntity item : order.getItems()) {
            productInternalApi.increaceProductStock(item.getSku(), item.getQuantity());
        }

        return OrderMapper.toResponse(orderRepository.save(order));

    }
}
