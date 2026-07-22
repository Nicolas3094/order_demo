package com.orders.messages.orders_demo.controllers;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orders.messages.orders_demo.dtos.request.CreateCustomerRequest;
import com.orders.messages.orders_demo.dtos.response.CustomerResponse;
import com.orders.messages.orders_demo.mappers.CustomerMapper;
import com.orders.messages.orders_demo.services.CustomerService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getCustomer(@PathVariable UUID id) {
        return ResponseEntity.ok(
                CustomerMapper.toResponse(
                        customerService.getCustomer(id)));
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(
            @Valid @RequestBody CreateCustomerRequest createCustomerRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CustomerMapper.toResponse(
                        customerService.createCustomer(createCustomerRequest)));
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<CustomerResponse> activateCustomer(@PathVariable UUID id) {
        return ResponseEntity.ok(
                CustomerMapper.toResponse(
                        customerService.activateCustomer(id)));
    }

    @PatchMapping("/{id}/block")
    public ResponseEntity<CustomerResponse> blockCustomer(@PathVariable UUID id) {
        return ResponseEntity.ok(
                CustomerMapper.toResponse(
                        customerService.deactivateCustomer(id)));
    }

}
