package com.orders.messages.orders_demo.app.customer.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.customer.api.CreateCustomerRequest;
import com.orders.messages.orders_demo.app.customer.api.CustomerResponse;
import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerBlockedException;
import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerNotFoundException;
import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerStateException;

class CustomerServiceIntegrationTest extends PostgresContainer {
    @Autowired
    private CustomerService customerService;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<UUID> customerIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            customerIds.forEach(customerRepository::deleteById);
            customerRepository.flush();
        });
        customerIds.clear();
    }

    @Test
    void shouldCreateAndPersistCustomer() {
        String email = UUID.randomUUID() + "@example.com";

        CustomerResponse result = customerService.createCustomer(new CreateCustomerRequest(email, "Test Customer"));
        customerIds.add(result.id());

        assertThat(result.id()).isNotNull();
        assertThat(result.email()).isEqualTo(email);
        assertThat(result.name()).isEqualTo("Test Customer");
        assertThat(result.status()).isEqualTo(CustomerStatus.ACTIVE);
        CustomerEntity persisted = readCustomer(result.id());
        assertThat(persisted.getEmail()).isEqualTo(email);
        assertThat(persisted.getName()).isEqualTo("Test Customer");
        assertThat(persisted.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(persisted.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldGetAllPersistedCustomers() {
        UUID firstId = persistCustomer(CustomerStatus.ACTIVE);
        UUID secondId = persistCustomer(CustomerStatus.BLOCKED);

        var result = customerService.getAllCustomers();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CustomerResponse::id).containsExactlyInAnyOrder(firstId, secondId);
        assertThat(result).extracting(CustomerResponse::status)
                .containsExactlyInAnyOrder(CustomerStatus.ACTIVE, CustomerStatus.BLOCKED);
    }

    @Test
    void shouldReturnEmptyListWhenNoCustomersExist() {
        assertThat(customerService.getAllCustomers()).isEmpty();
    }

    @Test
    void shouldGetPersistedCustomerById() {
        UUID customerId = persistCustomer(CustomerStatus.BLOCKED);
        CustomerEntity persisted = readCustomer(customerId);

        CustomerResponse result = customerService.getCustomer(customerId);

        assertThat(result.id()).isEqualTo(customerId);
        assertThat(result.email()).isEqualTo(persisted.getEmail());
        assertThat(result.name()).isEqualTo(persisted.getName());
        assertThat(result.status()).isEqualTo(CustomerStatus.BLOCKED);
    }

    @Test
    void shouldRejectGettingMissingCustomer() {
        assertThatThrownBy(() -> customerService.getCustomer(UUID.randomUUID()))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessage("Customer could not be found.");
    }

    @Test
    void shouldRejectCreatingCustomerWithDuplicateEmail() {
        UUID customerId = persistCustomer(CustomerStatus.ACTIVE);
        CustomerEntity original = readCustomer(customerId);
        long initialCount = customerRepository.count();

        assertThatThrownBy(() -> customerService.createCustomer(
                new CreateCustomerRequest(original.getEmail(), "Duplicate Customer")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(customerRepository.count()).isEqualTo(initialCount);
        assertThat(readCustomer(customerId).getName()).isEqualTo(original.getName());
    }

    @Test
    void shouldDeactivateAndPersistCustomerStatus() {
        UUID customerId = persistCustomer(CustomerStatus.ACTIVE);

        CustomerResponse result = customerService.deactivateCustomer(customerId);

        assertThat(result.id()).isEqualTo(customerId);
        assertThat(result.status()).isEqualTo(CustomerStatus.BLOCKED);
        assertThat(readCustomer(customerId).getStatus()).isEqualTo(CustomerStatus.BLOCKED);
    }

    @Test
    void shouldActivateAndPersistCustomerStatus() {
        UUID customerId = persistCustomer(CustomerStatus.BLOCKED);

        CustomerResponse result = customerService.activateCustomer(customerId);

        assertThat(result.id()).isEqualTo(customerId);
        assertThat(result.status()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(readCustomer(customerId).getStatus()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @ParameterizedTest
    @EnumSource(CustomerStatus.class)
    void shouldRejectInvalidCustomerStateTransitionsWithoutChangingPersistedState(CustomerStatus initialStatus) {
        UUID customerId = persistCustomer(initialStatus);

        if (initialStatus == CustomerStatus.ACTIVE) {
            assertThatThrownBy(() -> customerService.activateCustomer(customerId))
                    .isInstanceOf(CustomerStateException.class)
                    .hasMessage("Customer is already active.");
        } else {
            assertThatThrownBy(() -> customerService.deactivateCustomer(customerId))
                    .isInstanceOf(CustomerBlockedException.class)
                    .hasMessage("Blocked customer cannot be modified.");
        }

        assertThat(readCustomer(customerId).getStatus()).isEqualTo(initialStatus);
    }

    @Test
    void shouldRejectStateChangesForMissingCustomer() {
        UUID missingId = UUID.randomUUID();

        assertThatThrownBy(() -> customerService.activateCustomer(missingId))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessage("Customer could not be found.");
        assertThatThrownBy(() -> customerService.deactivateCustomer(missingId))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessage("Customer could not be found.");
    }

    @Test
    void shouldRollBackCustomerCreationWhenCallerTransactionFails() {
        long initialCount = customerRepository.count();
        List<UUID> createdIds = new ArrayList<>();
        String email = UUID.randomUUID() + "@example.com";

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            UUID id = customerService.createCustomer(new CreateCustomerRequest(email, "Test Customer")).id();
            createdIds.add(id);
            customerIds.add(id);
            entityManager.flush();
            throw new IllegalStateException("Caller transaction failed.");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Caller transaction failed.");

        assertThat(createdIds).hasSize(1);
        assertThat(customerRepository.findById(createdIds.get(0))).isEmpty();
        assertThat(customerRepository.count()).isEqualTo(initialCount);
    }

    @ParameterizedTest
    @EnumSource(CustomerStatus.class)
    void shouldRollBackCustomerStatusChangeWhenCallerTransactionFails(CustomerStatus initialStatus) {
        UUID customerId = persistCustomer(initialStatus);
        CustomerStatus changedStatus = initialStatus == CustomerStatus.ACTIVE ? CustomerStatus.BLOCKED : CustomerStatus.ACTIVE;

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            CustomerResponse result = initialStatus == CustomerStatus.ACTIVE
                    ? customerService.deactivateCustomer(customerId)
                    : customerService.activateCustomer(customerId);
            assertThat(result.status()).isEqualTo(changedStatus);
            entityManager.flush();
            entityManager.clear();
            assertThat(customerRepository.findById(customerId).orElseThrow().getStatus()).isEqualTo(changedStatus);
            throw new IllegalStateException("Caller transaction failed.");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Caller transaction failed.");

        assertThat(readCustomer(customerId).getStatus()).isEqualTo(initialStatus);
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

    private CustomerEntity readCustomer(UUID customerId) {
        return transactionTemplate.execute(status -> customerRepository.findById(customerId).orElseThrow());
    }
}
