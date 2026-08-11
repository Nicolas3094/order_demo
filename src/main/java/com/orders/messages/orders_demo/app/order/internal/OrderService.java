package com.orders.messages.orders_demo.app.order.internal;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerNotFoundException;
import com.orders.messages.orders_demo.app.order.api.CreateOrderRequest;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderNotFoundException;
import com.orders.messages.orders_demo.app.order_item.internal.OrderItemEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductRepository;
import com.orders.messages.orders_demo.app.product.internal.exceptions.ProductNotFoundException;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    public OrderService(OrderRepository repository,
            CustomerRepository customerRepository,
            ProductRepository productRepository) {
        this.orderRepository = repository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
    }

    /**
     * Retrieves all orders.
     *
     * @return a list containing all persisted orders.
     */
    public List<OrderEntity> getAllOrders() {
        return orderRepository.findAll();
    }

    /**
     * Retrieves an order by its identifier.
     *
     * @param id the order identifier.
     * @return the requested order.
     * @throws OrderNotFoundException if the order does not exist.
     */
    public OrderEntity getOrder(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(OrderNotFoundException::new);

    }

    /**
     * Creates a new order for the specified customer.
     *
     * @param createOrderRequest the information required to create the order.
     * @return the persisted order.
     * @throws CustomerNotFoundException if the customer does not exist.
     */
    public OrderEntity createOrder(CreateOrderRequest createOrderRequest) {
        CustomerEntity customer = findCustomerById(createOrderRequest.customerId());

        OrderEntity order = OrderMapper.toEntity(createOrderRequest, customer);

        return orderRepository.save(order);
    }

    /**
     * Cancels an order and restores the stock of all associated products.
     *
     * @param id the order identifier.
     * @return the updated order.
     * @throws OrderNotFoundException   if the order does not exist.
     * @throws ProductNotFoundException if any associated product cannot be found.
     */
    public OrderEntity cancelOrder(UUID id) {
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
    public OrderEntity expireOrder(UUID id) {
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
    public OrderEntity refundOrder(UUID id) {
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
    private OrderEntity restoreProductsStock(UUID id, Consumer<OrderEntity> action) {
        OrderEntity order = orderRepository.findById(id)
                .orElseThrow(OrderNotFoundException::new);

        action.accept(order);

        // TODO: Optimize product loading using findBySkuIn(...) and saveAll() to avoid
        // N+1 queries for large orders.
        for (OrderItemEntity item : order.getItems()) {
            ProductEntity product = productRepository.findBySku(item.getSku())
                    .orElseThrow(() -> new ProductNotFoundException(item.getSku()));

            product.increaseStock(item.getQuantity());

            productRepository.save(product);
        }

        return orderRepository.save(order);

    }

    /**
     * Retrieves a customer by its identifier.
     *
     * @param customerId the customer identifier.
     * @return the requested customer.
     * @throws CustomerNotFoundException if the customer does not exist.
     */
    private CustomerEntity findCustomerById(UUID customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(CustomerNotFoundException::new);
    }

}
