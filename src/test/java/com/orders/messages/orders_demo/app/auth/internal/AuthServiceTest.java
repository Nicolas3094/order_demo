package com.orders.messages.orders_demo.app.auth.internal;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.orders.messages.orders_demo.app.auth.api.RegisterRequest;
import com.orders.messages.orders_demo.app.auth.internal.exceptions.EmailAlreadyRegisteredException;
import com.orders.messages.orders_demo.app.customer.api.CustomerInternalApi;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final String EMAIL = "customer@example.com";
    private static final String NAME = "Test Customer";
    private static final String PASSWORD = "a-long-test-password";

    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private CustomerInternalApi customerInternalApi;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_WhenRequestIsValid_ShouldCreateCustomerThenSaveLinkedCustomerAccount() {
        var registerRequest = createRegisterRequest();
        var savedUserAccount = createSavedUserAccount();
        when(customerInternalApi.createCustomer(NAME, EMAIL)).thenReturn(CUSTOMER_ID);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("ENCODED-PASSWORD");
        when(userAccountRepository.save(any(UserAccountEntity.class))).thenReturn(savedUserAccount);

        var userAccountResponse = authService.register(registerRequest);

        var userAccountCaptor = ArgumentCaptor.forClass(UserAccountEntity.class);
        var registrationOrder = inOrder(customerInternalApi, userAccountRepository);
        registrationOrder.verify(customerInternalApi).createCustomer(NAME, EMAIL);
        registrationOrder.verify(userAccountRepository).save(userAccountCaptor.capture());
        assertThat(userAccountCaptor.getValue().getId()).isNull();
        assertThat(userAccountCaptor.getValue().getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(userAccountCaptor.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(userAccountCaptor.getValue().getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(userAccountResponse.id()).isEqualTo(ACCOUNT_ID);
        assertThat(userAccountResponse.email()).isEqualTo(EMAIL);
        assertThat(userAccountResponse.role()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    void register_WhenRequestIsValid_ShouldNeverPersistRawPassword() {
        var registerRequest = createRegisterRequest();
        when(customerInternalApi.createCustomer(NAME, EMAIL)).thenReturn(CUSTOMER_ID);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("ENCODED-PASSWORD");
        when(userAccountRepository.save(any(UserAccountEntity.class))).thenReturn(createSavedUserAccount());

        authService.register(registerRequest);

        var userAccountCaptor = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(userAccountRepository).save(userAccountCaptor.capture());
        assertThat(userAccountCaptor.getValue().getPasswordHash()).isNotBlank().isNotEqualTo(PASSWORD);
        assertThat(userAccountCaptor.getValue().getPasswordHash()).isEqualTo("ENCODED-PASSWORD");
    }

    @Test
    void register_WhenEmailIsAlreadyRegistered_ShouldRejectBeforeCreatingCustomerOrSavingAccount() {
        var registerRequest = createRegisterRequest();
        when(userAccountRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessage("Email is already registered.");

        verify(userAccountRepository).existsByEmail(EMAIL);
        verifyNoInteractions(customerInternalApi);
        verify(userAccountRepository, never()).save(any(UserAccountEntity.class));
    }

    @Test
    void register_WhenCustomerCreationFails_ShouldPropagateFailureWithoutSavingAccount() {
        var registerRequest = createRegisterRequest();
        var customerCreationFailure = new IllegalStateException("Customer creation failed.");
        when(customerInternalApi.createCustomer(NAME, EMAIL)).thenThrow(customerCreationFailure);

        assertThatThrownBy(() -> authService.register(registerRequest)).isSameAs(customerCreationFailure);

        verify(userAccountRepository, never()).save(any(UserAccountEntity.class));
    }

    @Test
    void register_WhenAccountSaveFails_ShouldPropagateOriginalFailure() {
        var registerRequest = createRegisterRequest();
        var accountSaveFailure = new IllegalStateException("Account save failed.");
        when(customerInternalApi.createCustomer(NAME, EMAIL)).thenReturn(CUSTOMER_ID);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("ENCODED-PASSWORD");
        when(userAccountRepository.save(any(UserAccountEntity.class))).thenThrow(accountSaveFailure);

        assertThatThrownBy(() -> authService.register(registerRequest)).isSameAs(accountSaveFailure);

        var registrationOrder = inOrder(customerInternalApi, userAccountRepository);
        registrationOrder.verify(customerInternalApi).createCustomer(NAME, EMAIL);
        registrationOrder.verify(userAccountRepository).save(any(UserAccountEntity.class));
    }

    private RegisterRequest createRegisterRequest() {
        return new RegisterRequest(EMAIL, PASSWORD, NAME);
    }

    private UserAccountEntity createSavedUserAccount() {
        return UserAccountEntity.builder()
                .id(ACCOUNT_ID)
                .customerId(CUSTOMER_ID)
                .email(EMAIL)
                .passwordHash("encoded-password-hash")
                .build();
    }
}
