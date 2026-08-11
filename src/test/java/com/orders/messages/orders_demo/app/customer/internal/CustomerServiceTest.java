package com.orders.messages.orders_demo.app.customer.internal;

import java.util.List;
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

import com.orders.messages.orders_demo.app.customer.api.CreateCustomerRequest;
import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerBlockedException;
import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerNotFoundException;
import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerStateException;

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
    public void getAllCustomers_ShouldReturnListOfCustomers() {
        UUID customerId_2 = UUID.randomUUID();
        when(customerRepository.findAll())
                .thenReturn(List.of(createActiveCustomer(customerId), createActiveCustomer(customerId_2)));

        java.util.List<CustomerEntity> result = customerService.getAllCustomers();

        assertEquals(2, result.size());
        assertEquals(customerId, result.get(0).getId());
        assertEquals(customerId_2, result.get(1).getId());
        assertEquals(DEFAULT_EMAIL, result.get(0).getEmail());
        assertEquals(DEFAULT_NAME, result.get(0).getName());
        assertEquals(CustomerStatus.ACTIVE, result.get(0).getStatus());
    }

    @Test
    public void getCustomer_WhenCustomerFound_ShouldReturnCustomer() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(createActiveCustomer(customerId)));

        CustomerEntity result = customerService.getCustomer(customerId);

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

        CustomerEntity result = customerService.createCustomer(DEFAULT_REQUEST);

        verify(customerRepository).save(result);
        assertEquals(DEFAULT_EMAIL, result.getEmail());
        assertEquals(DEFAULT_NAME, result.getName());
        assertEquals(CustomerStatus.ACTIVE, result.getStatus());
    }

    @Test
    public void deactivateCustomer_WhenCustomerFound_ShouldSaveCustomerAsBlocked() {
        CustomerEntity customer = createActiveCustomer(customerId);
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerEntity result = customerService.deactivateCustomer(customerId);

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
        CustomerEntity customer = createBlockedCustomer(customerId);
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        Exception result = assertThrows(CustomerBlockedException.class,
                () -> customerService.deactivateCustomer(customerId));

        assertEquals("Blocked customer cannot be modified.", result.getMessage());
        verify(customerRepository, never()).save(any());
    }

    @Test
    public void activateCustomer_WhenCustomerFound_ShouldSaveCustomerAsActive() {
        CustomerEntity customer = createBlockedCustomer(customerId);
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerEntity result = customerService.activateCustomer(customerId);

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
        CustomerEntity customer = createActiveCustomer(customerId);
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        Exception result = assertThrows(CustomerStateException.class,
                () -> customerService.activateCustomer(customerId));

        assertEquals("Customer is already active.", result.getMessage());
        verify(customerRepository, never()).save(any());
    }

    private static CustomerEntity createActiveCustomer(UUID customerId) {
        return new CustomerEntity(customerId, DEFAULT_EMAIL, DEFAULT_NAME, CustomerStatus.ACTIVE);
    }

    private static CustomerEntity createBlockedCustomer(UUID customerId) {
        return new CustomerEntity(customerId, DEFAULT_EMAIL, DEFAULT_NAME, CustomerStatus.BLOCKED);
    }
}
