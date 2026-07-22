package com.orders.messages.orders_demo.mappers;

import com.orders.messages.orders_demo.dtos.request.CreateCustomerRequest;
import com.orders.messages.orders_demo.dtos.response.CustomerResponse;
import com.orders.messages.orders_demo.entity.Customer;

public final class CustomerMapper {
    public static CustomerResponse toResponse(Customer customer) {
        return CustomerResponse.builder()
                .setId(customer.getId())
                .setEmail(customer.getEmail())
                .setName(customer.getName())
                .setStatus(customer.getStatus())
                .setCreatedAt(customer.getCreatedAt())
                .build();
    }

    public static Customer toEntity(CreateCustomerRequest createCustomerRequest) {
        return new Customer(createCustomerRequest.email(), createCustomerRequest.name());
    }
}
