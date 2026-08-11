package com.orders.messages.orders_demo.app.customer.internal;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.orders.messages.orders_demo.app.customer.api.CreateCustomerRequest;
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
    public List<CustomerEntity> getAllCustomers() {
        return customerRepository.findAll();
    }

    /**
     * Retrieves a customer by its identifier.
     *
     * @param id the customer identifier.
     * @return the customer associated with the given identifier.
     * @throws CustomerNotFoundException if the customer does not exist.
     */
    public CustomerEntity getCustomer(UUID id) {
        return customerRepository.findById(id).orElseThrow(CustomerNotFoundException::new);
    }

    /**
     * Creates a new customer.
     *
     * @param createCustomerRequest the customer information used for creation.
     * @return the persisted customer.
     */
    public CustomerEntity createCustomer(CreateCustomerRequest createCustomerRequest) {
        return customerRepository.save(CustomerMapper.toEntity(createCustomerRequest));
    }

    /**
     * Deactivates the customer with the given identifier.
     *
     * @param id the customer identifier.
     * @return the updated customer.
     * @throws CustomerNotFoundException if the customer does not exist.
     */
    public CustomerEntity deactivateCustomer(UUID id) {
        return updateCustomerState(id, CustomerEntity::deactivate);
    }

    /**
     * Activates the customer with the given identifier.
     *
     * @param id the customer identifier.
     * @return the updated customer.
     * @throws CustomerNotFoundException if the customer does not exist.
     */
    public CustomerEntity activateCustomer(UUID id) {
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
    private CustomerEntity updateCustomerState(UUID id, Consumer<CustomerEntity> action) {
        CustomerEntity customer = customerRepository.findById(id).orElseThrow(CustomerNotFoundException::new);

        action.accept(customer);

        return customerRepository.save(customer);
    }
}
