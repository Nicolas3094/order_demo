package com.orders.messages.orders_demo.app.customer.internal;

import com.orders.messages.orders_demo.app.customer.api.CreateCustomerRequest;
import com.orders.messages.orders_demo.app.customer.api.CustomerResponse;

public final class CustomerMapper {
    public static CustomerResponse toResponse(CustomerEntity customer) {
        return CustomerResponse.builder()
                .setId(customer.getId())
                .setEmail(customer.getEmail())
                .setName(customer.getName())
                .setStatus(customer.getStatus())
                .setCreatedAt(customer.getCreatedAt())
                .build();
    }

    public static CustomerEntity toEntity(CreateCustomerRequest createCustomerRequest) {
        return new CustomerEntity(createCustomerRequest.email(), createCustomerRequest.name());
    }
}
