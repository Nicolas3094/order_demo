package com.orders.messages.orders_demo.app.customer.internal;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerBlockedException;
import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerStateException;

public class CustomerEntityTest {

    private UUID customerId;

    private static final String DEFAULT_EMAIL = "customer_email";
    private static final String DEFAULT_NAME = "customer_name";

    @Test
    public void activate_WhenStatusIsBlocked_ShouldChangeStatusToActive() {
        customerId = UUID.randomUUID();
        CustomerEntity customer = createCustomerWithStatus(customerId, CustomerStatus.BLOCKED);

        customer.activate();

        assertEquals(CustomerStatus.ACTIVE, customer.getStatus());
    }

    @Test
    public void activate_WhenStatusIsActive_ShouldThrowCustomerStateExceptionWithMessage() {
        customerId = UUID.randomUUID();
        CustomerEntity customer = createCustomerWithStatus(customerId, CustomerStatus.ACTIVE);

        Exception result = assertThrows(CustomerStateException.class, () -> customer.activate());

        assertEquals("Customer is already active.", result.getMessage());
    }

    @Test
    public void deactivate_WhenStatusIsBlocked_ShouldThrowCustomerBlockedExceptionWithMessage() {
        customerId = UUID.randomUUID();
        CustomerEntity customer = createCustomerWithStatus(customerId, CustomerStatus.BLOCKED);

        Exception result = assertThrows(CustomerBlockedException.class, () -> customer.deactivate());

        assertEquals("Blocked customer cannot be modified.", result.getMessage());
    }

    @Test
    public void deactivate_WhenStatusIsActive_ShouldChangeStatusToBlocked() {
        customerId = UUID.randomUUID();
        CustomerEntity customer = createCustomerWithStatus(customerId, CustomerStatus.ACTIVE);

        customer.deactivate();

        assertEquals(CustomerStatus.BLOCKED, customer.getStatus());
    }

    private static CustomerEntity createCustomerWithStatus(UUID customerId, CustomerStatus status) {
        return new CustomerEntity(customerId, DEFAULT_EMAIL, DEFAULT_NAME, status);
    }

}
