package com.orders.messages.orders_demo.entity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.orders.messages.orders_demo.enums.CustomerStatus;
import com.orders.messages.orders_demo.exceptions.customer.CustomerStateException;

public class CustomerTest {

    private UUID customerId;

    private static final String DEFUALT_EMAIL = "customer_email";
    private static final String DEFAULT_NAME = "customer_name";

    @Test
    public void activate_WhenStatusIsBlocked_ShouldChangeStatusToActive() {
        customerId = UUID.randomUUID();
        Customer customer = createCustomerWithStatus(customerId, CustomerStatus.BLOCKED);

        customer.activate();

        assertEquals(CustomerStatus.ACTIVE, customer.getStatus());
    }

    @Test
    public void activate_WhenStatusIsActive_ShouldThrowCustomerStateExceptionWithMessage() {
        customerId = UUID.randomUUID();
        Customer customer = createCustomerWithStatus(customerId, CustomerStatus.ACTIVE);

        Exception result = assertThrows(CustomerStateException.class, () -> customer.activate());

        assertEquals("Customer is already active.", result.getMessage());
    }

    @Test
    public void deactivate_WhenStatusIsBlocked_ShouldThrowCustomerBlockedExceptionWithMessage() {
        customerId = UUID.randomUUID();
        Customer customer = createCustomerWithStatus(customerId, CustomerStatus.BLOCKED);

        Exception result = assertThrows(CustomerStateException.class, () -> customer.deactivate());

        assertEquals("Blocked customer cannot be modified.", result.getMessage());
    }

    @Test
    public void dactivate_WhenStatusIsActive_ShouldChangeStatusToBlocked() {
        customerId = UUID.randomUUID();
        Customer customer = createCustomerWithStatus(customerId, CustomerStatus.ACTIVE);

        customer.deactivate();

        assertEquals(CustomerStatus.BLOCKED, customer.getStatus());
    }

    private static Customer createCustomerWithStatus(UUID customerId, CustomerStatus status) {
        return new Customer(customerId, DEFUALT_EMAIL, DEFAULT_NAME, status);
    }

}
