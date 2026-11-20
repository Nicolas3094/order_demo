package com.orders.messages.orders_demo.app.customer.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsInAnyOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.customer.api.CreateCustomerRequest;

@AutoConfigureMockMvc
class CustomerControllerIntegrationTest extends PostgresContainer {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<UUID> customerIds = new ArrayList<>();
    private static final String BASE_PATH = "/api/v1/customers";

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            customerIds.forEach(customerRepository::deleteById);
            customerRepository.flush();
        });
        customerIds.clear();
    }

    @Test
    void shouldCreateCustomerThroughHttpAndReturn201WithPersistedData() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        CreateCustomerRequest request = new CreateCustomerRequest(email, "Test Customer");

        var result = mockMvc.perform(post(BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value("Test Customer"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        UUID customerId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        customerIds.add(customerId);

        transactionTemplate.executeWithoutResult(status -> {
            CustomerEntity customer = customerRepository.findById(customerId).orElseThrow();
            assertThat(customer.getEmail()).isEqualTo(email);
            assertThat(customer.getName()).isEqualTo("Test Customer");
            assertThat(customer.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
            assertThat(customer.getCreatedAt()).isNotNull();
        });
    }

    @Test
    void shouldListPersistedCustomersThroughHttpAndReturn200WithJsonArray() throws Exception {
        UUID firstId = persistCustomer(CustomerStatus.ACTIVE);
        UUID secondId = persistCustomer(CustomerStatus.BLOCKED);

        mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(firstId.toString(), secondId.toString())));
    }

    @Test
    void shouldGetPersistedCustomerThroughHttpAndReturn200WithMatchingJson() throws Exception {
        UUID customerId = persistCustomer(CustomerStatus.ACTIVE);
        CustomerEntity customer = customerRepository.findById(customerId).orElseThrow();

        mockMvc.perform(get(BASE_PATH + "/{id}", customerId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(customerId.toString()))
                .andExpect(jsonPath("$.email").value(customer.getEmail()))
                .andExpect(jsonPath("$.name").value(customer.getName()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldBlockCustomerThroughHttpAndReturn200WithPersistedStatus() throws Exception {
        UUID customerId = persistCustomer(CustomerStatus.ACTIVE);

        mockMvc.perform(patch(BASE_PATH + "/{id}/block", customerId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(customerId.toString()))
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        transactionTemplate.executeWithoutResult(status ->
                assertThat(customerRepository.findById(customerId).orElseThrow().getStatus())
                        .isEqualTo(CustomerStatus.BLOCKED));
    }

    @Test
    void shouldActivateCustomerThroughHttpAndReturn200WithPersistedStatus() throws Exception {
        UUID customerId = persistCustomer(CustomerStatus.BLOCKED);

        mockMvc.perform(patch(BASE_PATH + "/{id}/activate", customerId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(customerId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        transactionTemplate.executeWithoutResult(status ->
                assertThat(customerRepository.findById(customerId).orElseThrow().getStatus())
                        .isEqualTo(CustomerStatus.ACTIVE));
    }

    @Test
    void shouldRejectInvalidCreateRequestWith400AndErrorJsonWithoutPersistingCustomer() throws Exception {
        long initialCount = customerRepository.count();
        CreateCustomerRequest request = new CreateCustomerRequest(null, "Test Customer");

        mockMvc.perform(post(BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Customer email is required."))
                .andExpect(jsonPath("$.path").value(BASE_PATH));

        assertThat(customerRepository.count()).isEqualTo(initialCount);
    }

    @Test
    void shouldReturn404WithErrorJsonWhenCustomerDoesNotExist() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(get(BASE_PATH + "/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Customer could not be found."))
                .andExpect(jsonPath("$.path").value(BASE_PATH + "/" + missingId));
    }

    @Test
    void shouldReturn409WithErrorJsonForInvalidCustomerStateTransition() throws Exception {
        UUID customerId = persistCustomer(CustomerStatus.ACTIVE);

        mockMvc.perform(patch(BASE_PATH + "/{id}/activate", customerId))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Customer is already active."))
                .andExpect(jsonPath("$.path").value(BASE_PATH + "/" + customerId + "/activate"));

        transactionTemplate.executeWithoutResult(status ->
                assertThat(customerRepository.findById(customerId).orElseThrow().getStatus())
                        .isEqualTo(CustomerStatus.ACTIVE));
    }

    private UUID persistCustomer(CustomerStatus customerStatus) {
        return transactionTemplate.execute(status -> {
            CustomerEntity customer = new CustomerEntity(UUID.randomUUID() + "@example.com", "Test Customer");
            if (customerStatus == CustomerStatus.BLOCKED) {
                customer.deactivate();
            }
            UUID id = customerRepository.saveAndFlush(customer).getId();
            customerIds.add(id);
            return id;
        });
    }
}
