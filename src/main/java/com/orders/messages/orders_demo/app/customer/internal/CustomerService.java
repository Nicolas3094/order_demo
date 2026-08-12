package com.orders.messages.orders_demo.app.customer.internal;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.orders.messages.orders_demo.app.customer.api.CreateCustomerRequest;
import com.orders.messages.orders_demo.app.customer.api.CustomerResponse;
import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerNotFoundException;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /**
     * Retrieves all registered customers.
     *
     * @return a list containing all customers.
     */
    public List<CustomerResponse> getAllCustomers() {
        return customerRepository.findAll().stream()
                .map(CustomerMapper::toResponse)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Retrieves a customer by its identifier.
     *
     * @param id the customer identifier.
     * @return the customer associated with the given identifier.
     * @throws CustomerNotFoundException if the customer does not exist.
     */
    public CustomerResponse getCustomer(UUID id) {
        return CustomerMapper.toResponse(customerRepository.findById(id).orElseThrow(CustomerNotFoundException::new));
    }

    /**
     * Creates a new customer.
     *
     * @param createCustomerRequest the customer information used for creation.
     * @return the persisted customer.
     */
    public CustomerResponse createCustomer(CreateCustomerRequest createCustomerRequest) {
        return CustomerMapper.toResponse(customerRepository.save(CustomerMapper.toEntity(createCustomerRequest)));
    }

    /**
     * Deactivates the customer with the given identifier.
     *
     * @param id the customer identifier.
     * @return the updated customer.
     * @throws CustomerNotFoundException if the customer does not exist.
     */
    public CustomerResponse deactivateCustomer(UUID id) {
        return updateCustomerState(id, CustomerEntity::deactivate);
    }

    /**
     * Activates the customer with the given identifier.
     *
     * @param id the customer identifier.
     * @return the updated customer.
     * @throws CustomerNotFoundException if the customer does not exist.
     */
    public CustomerResponse activateCustomer(UUID id) {
        return updateCustomerState(id, CustomerEntity::activate);
    }

    /**
     * Applies the given state transition to a customer and persists the changes.
     *
     * @param id     the customer identifier.
     * @param action the state transition to apply.
     * @return the updated customer.
     * @throws CustomerNotFoundException if the customer does not exist.
     */
    private CustomerResponse updateCustomerState(UUID id, Consumer<CustomerEntity> action) {
        CustomerEntity customer = customerRepository.findById(id).orElseThrow(CustomerNotFoundException::new);

        action.accept(customer);

        return CustomerMapper.toResponse(customerRepository.save(customer));
    }
}
