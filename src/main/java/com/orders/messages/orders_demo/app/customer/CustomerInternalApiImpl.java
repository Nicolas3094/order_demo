package com.orders.messages.orders_demo.app.customer;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.orders.messages.orders_demo.app.customer.api.CustomerInternalApi;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerNotFoundException;

@Service
public class CustomerInternalApiImpl implements CustomerInternalApi {

    private final CustomerRepository customerRepository;

    public CustomerInternalApiImpl(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public CustomerEntity getCustomer(UUID id) {
        return customerRepository.findById(id).orElseThrow(CustomerNotFoundException::new);
    }

}
