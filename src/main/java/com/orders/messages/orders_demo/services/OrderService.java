package com.orders.messages.orders_demo.services;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.orders.messages.orders_demo.dtos.request.CreateOrderRequest;
import com.orders.messages.orders_demo.entity.Customer;
import com.orders.messages.orders_demo.entity.Order;
import com.orders.messages.orders_demo.exceptions.CustomerNotFoundException;
import com.orders.messages.orders_demo.exceptions.OrderNotFoundException;
import com.orders.messages.orders_demo.mappers.OrderMapper;
import com.orders.messages.orders_demo.repositories.CustomerRepository;
import com.orders.messages.orders_demo.repositories.OrderRepository;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;

    public OrderService(OrderRepository repository, CustomerRepository customerRepository) {
        this.orderRepository = repository;
        this.customerRepository = customerRepository;
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

    public void cancelOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(OrderNotFoundException::new);

        order.cancelOrder();

        orderRepository.save(order);
    }

    public void payOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(OrderNotFoundException::new);

        order.markAsPaid();

        orderRepository.save(order);
    }

    public void expireOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(OrderNotFoundException::new);

        order.expire();

        orderRepository.save(order);
    }

    public void refundOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(OrderNotFoundException::new);

        order.refund();

        orderRepository.save(order);
    }

}
