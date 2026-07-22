package com.orders.messages.orders_demo.dtos.request;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload used to create a new customer.
 *
 * @param email the email of the customer.
 * @param name  the name of the customer.
 */
public record CreateCustomerRequest(
        @NotNull(message = "Customer email is required.") String email,
        @NotNull(message = "Customer name is required.") String name) {
}
