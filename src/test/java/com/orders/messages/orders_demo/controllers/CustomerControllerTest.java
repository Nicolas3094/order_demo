package com.orders.messages.orders_demo.controllers;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.messages.orders_demo.dtos.request.CreateCustomerRequest;
import com.orders.messages.orders_demo.entity.Customer;
import com.orders.messages.orders_demo.enums.CustomerStatus;
import com.orders.messages.orders_demo.exceptions.customer.CustomerBlockedException;
import com.orders.messages.orders_demo.exceptions.customer.CustomerNotFoundException;
import com.orders.messages.orders_demo.exceptions.customer.CustomerStateException;
import com.orders.messages.orders_demo.services.CustomerService;

@WebMvcTest(CustomerController.class)
public class CustomerControllerTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CustomerService customerService;

    private static final String DEFAULT_EMAIL = "customer_email";
    private static final String DEFAULT_NAME = "customer_name";

    private UUID customerId;
    private CreateCustomerRequest validCreateCustomerRequest;

    @BeforeEach
    public void setup() {
        customerId = UUID.randomUUID();
        validCreateCustomerRequest = createValidRequest();
    }

    @Test
    public void getCustomer_ShouldReturn200() throws Exception {
        Customer customer = createActiveCustomer(customerId);
        when(customerService.getCustomer(customerId)).thenReturn(customer);

        mvc.perform(get("/api/v1/customers/{id}", customerId)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(customerId.toString()))
                .andExpect(jsonPath("$.email").value(DEFAULT_EMAIL))
                .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        verify(customerService).getCustomer(customerId);
    }

    @Test
    void getCustomer_WhenCustomerNotFound_ShouldReturn404() throws Exception {
        when(customerService.getCustomer(customerId))
                .thenThrow(new CustomerNotFoundException());

        mvc.perform(get("/api/v1/customers/{id}", customerId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Customer could not be found."))
                .andExpect(jsonPath("$.path").value("/api/v1/customers/" + customerId));
        verify(customerService).getCustomer(customerId);
    }

    @Test
    public void createCustomer_WhenRequestIsValid_ShouldReturn201() throws Exception {
        Customer customer = createActiveCustomer(customerId);
        when(customerService.createCustomer(validCreateCustomerRequest)).thenReturn(customer);

        mvc.perform(post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreateCustomerRequest)))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(customerId.toString()))
                .andExpect(jsonPath("$.email").value(DEFAULT_EMAIL))
                .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        verify(customerService).createCustomer(validCreateCustomerRequest);
    }

    @Test
    public void createCustomer_WhenCustomerDoesNotExist_ShouldReturn404() throws Exception {
        when(customerService.createCustomer(validCreateCustomerRequest)).thenThrow(new CustomerNotFoundException());

        mvc.perform(post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreateCustomerRequest)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Customer could not be found."))
                .andExpect(jsonPath("$.path").value("/api/v1/customers"));
        verify(customerService).createCustomer(validCreateCustomerRequest);
    }

    @Test
    public void createCustomer_WhenValidationFailsWithNullEmail_ShouldReturn400() throws Exception {
        CreateCustomerRequest request = new CreateCustomerRequest(null, DEFAULT_NAME);

        mvc.perform(post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Customer email is required."))
                .andExpect(jsonPath("$.path").value("/api/v1/customers"));
        verify(customerService, never()).createCustomer(any(CreateCustomerRequest.class));
    }

    @Test
    public void createCustomer_WhenValidationFailsWithNullName_ShouldReturn400() throws Exception {
        CreateCustomerRequest request = new CreateCustomerRequest(DEFAULT_EMAIL, null);

        mvc.perform(post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Customer name is required."))
                .andExpect(jsonPath("$.path").value("/api/v1/customers"));
        verify(customerService, never()).createCustomer(any(CreateCustomerRequest.class));
    }

    @Test
    public void activateCustomer_ShouldReturn200() throws Exception {
        Customer customer = createActiveCustomer(customerId);
        when(customerService.activateCustomer(customerId)).thenReturn(customer);

        mvc.perform(patch("/api/v1/customers/{id}/activate", customerId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(customerId.toString()))
                .andExpect(jsonPath("$.email").value(DEFAULT_EMAIL))
                .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        verify(customerService).activateCustomer(customerId);
    }

    @Test
    public void activateCustomer_WhenCustomerIdNotFound_ShouldReturn404() throws Exception {
        when(customerService.activateCustomer(customerId)).thenThrow(new CustomerNotFoundException());

        mvc.perform(patch("/api/v1/customers/{id}/activate", customerId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Customer could not be found."))
                .andExpect(jsonPath("$.path").value("/api/v1/customers/" + customerId + "/activate"));
        verify(customerService).activateCustomer(customerId);
    }

    @Test
    public void activateCustomer_WhenCustomerIsAlreadyActive_ShouldReturn409() throws Exception {
        when(customerService.activateCustomer(customerId))
                .thenThrow(new CustomerStateException("Customer is already active."));

        mvc.perform(patch("/api/v1/customers/{id}/activate", customerId))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Customer is already active."))
                .andExpect(jsonPath("$.path").value("/api/v1/customers/" + customerId + "/activate"));
    }

    @Test
    public void blockCustomer_ShouldReturn200() throws Exception {
        Customer customer = createBlockedCustomer(customerId);
        when(customerService.deactivateCustomer(customerId)).thenReturn(customer);

        mvc.perform(patch("/api/v1/customers/{id}/block", customerId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(customerId.toString()))
                .andExpect(jsonPath("$.email").value(DEFAULT_EMAIL))
                .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
                .andExpect(jsonPath("$.status").value("BLOCKED"));
        verify(customerService).deactivateCustomer(customerId);
    }

    @Test
    public void blockCustomer_WhenCustomerIdNotFound_ShouldReturn404() throws Exception {
        when(customerService.deactivateCustomer(customerId)).thenThrow(new CustomerNotFoundException());

        mvc.perform(patch("/api/v1/customers/{id}/block", customerId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Customer could not be found."))
                .andExpect(jsonPath("$.path").value("/api/v1/customers/" + customerId + "/block"));
        verify(customerService).deactivateCustomer(customerId);
    }

    @Test
    public void blockCustomer_WhenCustomerIsAlreadyBlocked_ShouldReturn409() throws Exception {
        when(customerService.deactivateCustomer(customerId))
                .thenThrow(new CustomerBlockedException());

        mvc.perform(patch("/api/v1/customers/{id}/block", customerId))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Blocked customer cannot be modified."))
                .andExpect(jsonPath("$.path").value("/api/v1/customers/" + customerId + "/block"));
    }

    private static CreateCustomerRequest createValidRequest() {
        return new CreateCustomerRequest(DEFAULT_EMAIL, DEFAULT_NAME);
    }

    private static Customer createActiveCustomer(UUID customerId) {
        return new Customer(customerId, DEFAULT_EMAIL, DEFAULT_NAME, CustomerStatus.ACTIVE);
    }

    private static Customer createBlockedCustomer(UUID customerId) {
        return new Customer(customerId, DEFAULT_EMAIL, DEFAULT_NAME, CustomerStatus.BLOCKED);
    }

}
