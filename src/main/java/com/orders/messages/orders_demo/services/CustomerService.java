package com.orders.messages.orders_demo.services;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.orders.messages.orders_demo.dtos.request.CreateCustomerRequest;
import com.orders.messages.orders_demo.entity.Customer;
import com.orders.messages.orders_demo.exceptions.customer.CustomerNotFoundException;
import com.orders.messages.orders_demo.mappers.CustomerMapper;
import com.orders.messages.orders_demo.repositories.CustomerRepository;

@Service
public class CustomerService {
    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Customer getCustomer(UUID id) {
        return customerRepository.findById(id).orElseThrow(CustomerNotFoundException::new);
    }

    public Customer createCustomer(CreateCustomerRequest createCustomerRequest) {
        return customerRepository.save(CustomerMapper.toEntity(createCustomerRequest));
    }

    public Customer deactivateCustomer(UUID id) {
        Customer customer = customerRepository.findById(id).orElseThrow(CustomerNotFoundException::new);

        customer.deactivate();

        return customerRepository.save(customer);
    }

    public Customer activateCustomer(UUID id) {
        Customer customer = customerRepository.findById(id).orElseThrow(CustomerNotFoundException::new);

        customer.activate();

        return customerRepository.save(customer);
    }
}
