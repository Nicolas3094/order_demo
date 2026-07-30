package com.orders.messages.orders_demo.services;

import java.util.UUID;

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

    public Order getOrder(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(OrderNotFoundException::new);

    }

    public Order createOrder(CreateOrderRequest createOrderRequest) {
        Customer customer = customerRepository.findById(createOrderRequest.customerId())
                .orElseThrow(CustomerNotFoundException::new);

        Order order = OrderMapper.toEntity(createOrderRequest, customer);

        return orderRepository.save(order);
    }

    public Order cancelOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(OrderNotFoundException::new);

        order.cancelOrder();

        for (OrderItem item : order.getItems()) {
            Product product = productRepository.findBySku(item.getSku())
                    .orElseThrow(
                            () -> new ProductNotFoundException(item.getSku()));

            product.increaseStock(item.getQuantity());

            productRepository.save(product);
        }

        return orderRepository.save(order);
    }

    public Order expireOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(OrderNotFoundException::new);

        order.expire();

        return orderRepository.save(order);
    }

    public Order refundOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(OrderNotFoundException::new);

        order.refund();

        return orderRepository.save(order);
    }

}
