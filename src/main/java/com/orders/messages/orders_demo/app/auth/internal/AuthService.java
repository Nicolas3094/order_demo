package com.orders.messages.orders_demo.app.auth.internal;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orders.messages.orders_demo.app.auth.api.LoginRequest;
import com.orders.messages.orders_demo.app.auth.api.RegisterRequest;
import com.orders.messages.orders_demo.app.auth.api.UserAccountResponse;
import com.orders.messages.orders_demo.app.auth.internal.exceptions.EmailAlreadyRegisteredException;
import com.orders.messages.orders_demo.app.auth.internal.exceptions.InvalidCredentialsException;
import com.orders.messages.orders_demo.app.customer.api.CustomerInternalApi;
import com.orders.messages.orders_demo.app.customer.api.CustomerResponse;
import com.orders.messages.orders_demo.app.customer.internal.CustomerStatus;
import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerNotFoundException;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final CustomerInternalApi customerInternalApi;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserAccountRepository userAccountRepository,
            CustomerInternalApi customerInternalApi,
            PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.customerInternalApi = customerInternalApi;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserAccountResponse register(RegisterRequest registerRequest) {

        if (userAccountRepository.existsByEmail(registerRequest.email())) {
            throw new EmailAlreadyRegisteredException();
        }

        UUID customerId = customerInternalApi.createCustomer(registerRequest.name(), registerRequest.email());

        var userAccount = userAccountRepository.save(UserAccountMapper.toEntity(
                registerRequest.email(),
                passwordEncoder.encode(registerRequest.password()),
                customerId));

        return UserAccountMapper.toResponse(userAccount);
    }

    @Transactional
    public UserAccountResponse login(LoginRequest loginRequest) {

        UserAccountEntity userAccountEntity = userAccountRepository.findByEmail(loginRequest.email())
                .orElseThrow(InvalidCredentialsException::new);

        validatePasswordAndEmail(loginRequest, userAccountEntity);

        validateCustomerStatus(userAccountEntity.getCustomerId());

        return UserAccountMapper.toResponse(userAccountEntity);
    }

    private void validatePasswordAndEmail(LoginRequest loginRequest, UserAccountEntity userAccountEntity) {
        if (!passwordEncoder.matches(loginRequest.password(), userAccountEntity.getPasswordHash())
                || !loginRequest.email().equals(userAccountEntity.getEmail())) {
            throw new InvalidCredentialsException();
        }
    }

    private void validateCustomerStatus(UUID customerId) {
        CustomerResponse persistedCustomer;
        try {
            persistedCustomer = customerInternalApi.getCustomer(customerId);
        } catch (CustomerNotFoundException e) {
            throw new InvalidCredentialsException();
        }

        if (CustomerStatus.BLOCKED.equals(persistedCustomer.status())) {
            throw new InvalidCredentialsException();
        }
    }

}
