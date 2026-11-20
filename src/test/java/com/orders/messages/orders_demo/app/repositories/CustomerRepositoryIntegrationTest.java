package com.orders.messages.orders_demo.app.repositories;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.customer.internal.CustomerStatus;

@Transactional
class CustomerRepositoryIntegrationTest extends PostgresContainer {
    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void shouldPersistAndReloadCustomerWithAllMappedFields() {
        String email = UUID.randomUUID() + "@example.com";
        CustomerEntity saved = customerRepository.saveAndFlush(new CustomerEntity(email, "Test Customer"));
        UUID customerId = saved.getId();
        entityManager.clear();

        CustomerEntity result = customerRepository.findById(customerId).orElseThrow();

        assertThat(result).isNotSameAs(saved);
        assertThat(result.getId()).isEqualTo(customerId);
        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.getName()).isEqualTo("Test Customer");
        assertThat(result.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(result.getCreatedAt()).isNotNull().isEqualTo(saved.getCreatedAt());
    }

    @Test
    void shouldRejectDuplicateEmail() {
        String email = UUID.randomUUID() + "@example.com";
        customerRepository.saveAndFlush(new CustomerEntity(email, "First Customer"));
        entityManager.clear();

        assertThatThrownBy(() -> customerRepository.saveAndFlush(new CustomerEntity(email, "Second Customer")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("uk_customer_email");
    }

    @Test
    void shouldPersistCustomerStatusChanges() {
        UUID customerId = customerRepository.saveAndFlush(createCustomer()).getId();
        entityManager.clear();
        CustomerEntity customer = customerRepository.findById(customerId).orElseThrow();

        customer.deactivate();
        customerRepository.saveAndFlush(customer);
        entityManager.clear();

        CustomerEntity blocked = customerRepository.findById(customerId).orElseThrow();
        assertThat(blocked.getStatus()).isEqualTo(CustomerStatus.BLOCKED);

        blocked.activate();
        customerRepository.saveAndFlush(blocked);
        entityManager.clear();

        assertThat(customerRepository.findById(customerId).orElseThrow().getStatus()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    void shouldRejectNameExceedingColumnLength() {
        String email = UUID.randomUUID() + "@example.com";
        customerRepository.saveAndFlush(new CustomerEntity(email, "N".repeat(100)));
        entityManager.clear();

        assertThatThrownBy(() -> customerRepository.saveAndFlush(
                new CustomerEntity(UUID.randomUUID() + "@example.com", "N".repeat(101))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("value too long for type character varying(100)");
    }

    @Test
    void shouldRejectEmailExceedingColumnLength() {
        String validEmail = "e".repeat(243) + "@example.com";
        customerRepository.saveAndFlush(new CustomerEntity(validEmail, "Test Customer"));
        entityManager.clear();

        assertThatThrownBy(() -> customerRepository.saveAndFlush(
                new CustomerEntity("e" + validEmail, "Test Customer")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("value too long for type character varying(255)");
    }

    private CustomerEntity createCustomer() {
        return new CustomerEntity(UUID.randomUUID() + "@example.com", "Test Customer");
    }
}
