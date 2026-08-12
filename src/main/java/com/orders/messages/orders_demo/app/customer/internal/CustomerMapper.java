package com.orders.messages.orders_demo.app.customer.internal;

import com.orders.messages.orders_demo.app.customer.api.CreateCustomerRequest;
import com.orders.messages.orders_demo.app.customer.api.CustomerResponse;

public final class CustomerMapper {
    public static CustomerResponse toResponse(CustomerEntity customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .email(customer.getEmail())
                .name(customer.getName())
                .status(customer.getStatus())
                .createdAt(customer.getCreatedAt())
                .build();
    }

    public static CustomerEntity toEntity(CreateCustomerRequest createCustomerRequest) {
        return new CustomerEntity(createCustomerRequest.email(), createCustomerRequest.name());
    }
}
