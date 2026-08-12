package com.orders.messages.orders_demo.app.customer;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orders.messages.orders_demo.app.customer.api.CustomerResponse;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerMapper;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerNotFoundException;

@ExtendWith(MockitoExtension.class)
public class CustomerInternalApiImplTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerInternalApiImpl customerInternalApi;

    @Test
    public void getCustomer_WhenCustomerExists_ShouldReturnCustomerResponse() {
        CustomerEntity customer = new CustomerEntity("email", "name");
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        CustomerResponse expected = CustomerMapper.toResponse(customer);

        CustomerResponse result = customerInternalApi.getCustomer(CUSTOMER_ID);

        assertEquals(expected, result);
        verify(customerRepository).findById(CUSTOMER_ID);
    }

    @Test
    public void getCustomer_WhenCustomerDoesNotExist_ShouldThrowCustomerNotFoundException() {
        when(customerRepository.findById(CUSTOMER_ID))
                .thenReturn(Optional.empty());

        CustomerNotFoundException result = assertThrows(CustomerNotFoundException.class,
                () -> customerInternalApi.getCustomer(CUSTOMER_ID));

        verify(customerRepository).findById(CUSTOMER_ID);
        assertEquals("Customer could not be found.", result.getMessage());
    }
}