package com.orders.messages.orders_demo.services;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.orders.messages.orders_demo.dtos.request.CreateCustomerRequest;
import com.orders.messages.orders_demo.entity.Customer;
import com.orders.messages.orders_demo.exceptions.customer.CustomerNotFoundException;
import com.orders.messages.orders_demo.mappers.CustomerMapper;
import com.orders.messages.orders_demo.repositories.CustomerRepository;

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
    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    /**
     * Retrieves a customer by its identifier.
     *
     * @param id the customer identifier.
     * @return the customer associated with the given identifier.
     * @throws CustomerNotFoundException if the customer does not exist.
     */
    public Customer getCustomer(UUID id) {
        return customerRepository.findById(id).orElseThrow(CustomerNotFoundException::new);
    }

    /**
     * Creates a new customer.
     *
     * @param createCustomerRequest the customer information used for creation.
     * @return the persisted customer.
     */
    public Customer createCustomer(CreateCustomerRequest createCustomerRequest) {
        return customerRepository.save(CustomerMapper.toEntity(createCustomerRequest));
    }

    /**
     * Deactivates the customer with the given identifier.
     *
     * @param id the customer identifier.
     * @return the updated customer.
     * @throws CustomerNotFoundException if the customer does not exist.
     */
    public Customer deactivateCustomer(UUID id) {
        return updateCustomerState(id, Customer::deactivate);
    }

    /**
     * Activates the customer with the given identifier.
     *
     * @param id the customer identifier.
     * @return the updated customer.
     * @throws CustomerNotFoundException if the customer does not exist.
     */
    public Customer activateCustomer(UUID id) {
        return updateCustomerState(id, Customer::activate);
    }

    /**
     * Applies the given state transition to a customer and persists the changes.
     *
     * @param id     the customer identifier.
     * @param action the state transition to apply.
     * @return the updated customer.
     * @throws CustomerNotFoundException if the customer does not exist.
     */
    private Customer updateCustomerState(UUID id, Consumer<Customer> action) {
        Customer customer = customerRepository.findById(id).orElseThrow(CustomerNotFoundException::new);

        action.accept(customer);

        return customerRepository.save(customer);
    }
}
