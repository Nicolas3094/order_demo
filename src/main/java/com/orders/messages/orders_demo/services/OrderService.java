package com.orders.messages.orders_demo.services;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.orders.messages.orders_demo.dtos.request.CreateOrderRequest;
import com.orders.messages.orders_demo.entity.Customer;
import com.orders.messages.orders_demo.entity.Order;
import com.orders.messages.orders_demo.entity.OrderItem;
import com.orders.messages.orders_demo.entity.Product;
import com.orders.messages.orders_demo.exceptions.customer.CustomerNotFoundException;
import com.orders.messages.orders_demo.exceptions.orders.OrderNotFoundException;
import com.orders.messages.orders_demo.exceptions.product.ProductNotFoundException;
import com.orders.messages.orders_demo.mappers.OrderMapper;
import com.orders.messages.orders_demo.repositories.CustomerRepository;
import com.orders.messages.orders_demo.repositories.OrderRepository;
import com.orders.messages.orders_demo.repositories.ProductRepository;

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
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    /**
     * Retrieves an order by its identifier.
     *
     * @param id the order identifier.
     * @return the requested order.
     * @throws OrderNotFoundException if the order does not exist.
     */
    public Order getOrder(UUID id) {
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
    public Order createOrder(CreateOrderRequest createOrderRequest) {
        Customer customer = findCustomerById(createOrderRequest.customerId());

        Order order = OrderMapper.toEntity(createOrderRequest, customer);

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
    public Order cancelOrder(UUID id) {
        return restoreProductsStock(id, Order::cancelOrder);
    }

    /**
     * Expires an order and restores the stock of all associated products.
     *
     * @param id the order identifier.
     * @return the updated order.
     * @throws OrderNotFoundException   if the order does not exist.
     * @throws ProductNotFoundException if any associated product cannot be found.
     */
    public Order expireOrder(UUID id) {
        return restoreProductsStock(id, Order::expire);
    }

    /**
     * Refunds an order and restores the stock of all associated products.
     *
     * @param id the order identifier.
     * @return the updated order.
     * @throws OrderNotFoundException   if the order does not exist.
     * @throws ProductNotFoundException if any associated product cannot be found.
     */
    public Order refundOrder(UUID id) {
        return restoreProductsStock(id, Order::refund);
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
    private Order restoreProductsStock(UUID id, Consumer<Order> action) {
        Order order = orderRepository.findById(id)
                .orElseThrow(OrderNotFoundException::new);

        action.accept(order);

        // TODO: Optimize product loading using findBySkuIn(...) and saveAll() to avoid
        // N+1 queries for large orders.
        for (OrderItem item : order.getItems()) {
            Product product = productRepository.findBySku(item.getSku())
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
    private Customer findCustomerById(UUID customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(CustomerNotFoundException::new);
    }

}
