package com.orders.messages.orders_demo.services;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orders.messages.orders_demo.dtos.request.CreateCustomerRequest;
import com.orders.messages.orders_demo.entity.Customer;
import com.orders.messages.orders_demo.enums.CustomerStatus;
import com.orders.messages.orders_demo.exceptions.customer.CustomerBlockedException;
import com.orders.messages.orders_demo.exceptions.customer.CustomerNotFoundException;
import com.orders.messages.orders_demo.exceptions.customer.CustomerStateException;
import com.orders.messages.orders_demo.repositories.CustomerRepository;

@ExtendWith(MockitoExtension.class)
public class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    private UUID customerId;

    private static final String DEFAULT_EMAIL = "customer_email";
    private static final String DEFAULT_NAME = "customer_name";
    private static final CreateCustomerRequest DEFAULT_REQUEST = new CreateCustomerRequest(DEFAULT_EMAIL, DEFAULT_NAME);

    @BeforeEach
    public void setup() {
        customerId = UUID.randomUUID();
    }

    @Test
    public void getCustomer_WhenCustomerFound_ShouldReturnCustomer() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(createActiveCustomer(customerId)));

        Customer result = customerService.getCustomer(customerId);

        assertEquals(customerId, result.getId());
        assertEquals(DEFAULT_EMAIL, result.getEmail());
        assertEquals(DEFAULT_NAME, result.getName());
        assertEquals(CustomerStatus.ACTIVE, result.getStatus());
    }

    @Test
    public void getCustomer_WhenCustomerNotFound_ShouldThrowCustomerNotFoundException() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

        Exception result = assertThrows(CustomerNotFoundException.class, () -> customerService.getCustomer(customerId));

        assertEquals("Customer could not be found.", result.getMessage());
    }

    @Test
    public void createCustomer_ShouldSaveEntity() {
        when(customerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Customer result = customerService.createCustomer(DEFAULT_REQUEST);

        verify(customerRepository).save(result);
        assertEquals(DEFAULT_EMAIL, result.getEmail());
        assertEquals(DEFAULT_NAME, result.getName());
        assertEquals(CustomerStatus.ACTIVE, result.getStatus());
    }

    @Test
    public void deactivateCustomer_WhenCustomerFound_ShouldSaveCustomerAsBlocked() {
        Customer customer = createActiveCustomer(customerId);
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Customer result = customerService.deactivateCustomer(customerId);

        verify(customerRepository).save(customer);
        assertEquals(customerId, result.getId());
        assertEquals(DEFAULT_EMAIL, result.getEmail());
        assertEquals(DEFAULT_NAME, result.getName());
        assertEquals(CustomerStatus.BLOCKED, result.getStatus());
    }

    @Test
    public void deactivateCustomer_WhenNotCustomerFound_ShouldThrowCustomerNotFoundException() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

        Exception result = assertThrows(CustomerNotFoundException.class,
                () -> customerService.deactivateCustomer(customerId));

        assertEquals("Customer could not be found.", result.getMessage());
    }

    @Test
    public void deactivateCustomer_WhenCustomerIsBlocked_ShouldThrowCustomerBlockedException() {
        Customer customer = createBlockedCustomer(customerId);
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        Exception result = assertThrows(CustomerBlockedException.class,
                () -> customerService.deactivateCustomer(customerId));

        assertEquals("Blocked customer cannot be modified.", result.getMessage());
        verify(customerRepository, never()).save(any());
    }

    @Test
    public void activateCustomer_WhenCustomerFound_ShouldSaveCustomerAsActive() {
        Customer customer = createBlockedCustomer(customerId);
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Customer result = customerService.activateCustomer(customerId);

        verify(customerRepository).save(customer);
        assertEquals(customerId, result.getId());
        assertEquals(DEFAULT_EMAIL, result.getEmail());
        assertEquals(DEFAULT_NAME, result.getName());
        assertEquals(CustomerStatus.ACTIVE, result.getStatus());
    }

    @Test
    public void activateCustomer_WhenNotCustomerFound_ShouldThrowCustomerNotFoundException() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

        Exception result = assertThrows(CustomerNotFoundException.class,
                () -> customerService.activateCustomer(customerId));

        assertEquals("Customer could not be found.", result.getMessage());
    }

    @Test
    public void activateCustomer_WhenCustomerIsActive_ShouldThrowCustomerStateException() {
        Customer customer = createActiveCustomer(customerId);
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        Exception result = assertThrows(CustomerStateException.class,
                () -> customerService.activateCustomer(customerId));

        assertEquals("Customer is already active.", result.getMessage());
        verify(customerRepository, never()).save(any());
    }

    private static Customer createActiveCustomer(UUID customerId) {
        return new Customer(customerId, DEFAULT_EMAIL, DEFAULT_NAME, CustomerStatus.ACTIVE);
    }

    private static Customer createBlockedCustomer(UUID customerId) {
        return new Customer(customerId, DEFAULT_EMAIL, DEFAULT_NAME, CustomerStatus.BLOCKED);
    }
}
