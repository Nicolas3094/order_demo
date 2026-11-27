package com.orders.messages.orders_demo.app.auth.internal;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
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
import org.springframework.dao.DataAccessResourceFailureException;

import com.orders.messages.orders_demo.app.auth.api.LoginRequest;
import com.orders.messages.orders_demo.app.auth.api.RegisterRequest;
import com.orders.messages.orders_demo.app.auth.internal.exceptions.EmailAlreadyRegisteredException;
import com.orders.messages.orders_demo.app.auth.internal.exceptions.InvalidCredentialsException;
import com.orders.messages.orders_demo.app.customer.api.CustomerInternalApi;
import com.orders.messages.orders_demo.app.customer.api.CustomerResponse;
import com.orders.messages.orders_demo.app.customer.internal.CustomerStatus;
import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerNotFoundException;

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

    /*
     * REGISTER
     */

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

    /*
     * LOG IN
     */

    @Test
    void login_WhenAccountSuccessfullyLoggedIn_ShouldReturnResponse() {
        var userAccountEntity = createSavedUserAccount();
        var loginRequest = new LoginRequest(EMAIL, PASSWORD);
        when(userAccountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userAccountEntity));
        when(customerInternalApi.getCustomer(CUSTOMER_ID)).thenReturn(CustomerResponse.builder()
                .id(CUSTOMER_ID)
                .email(EMAIL)
                .name(NAME)
                .status(CustomerStatus.ACTIVE)
                .build());
        when(passwordEncoder.matches(PASSWORD, "encoded-password-hash")).thenReturn(true);

        var result = authService.login(loginRequest);

        assertThat(result.id()).isEqualTo(ACCOUNT_ID);
        assertThat(result.email()).isEqualTo(EMAIL);
        assertThat(result.role()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    void login_WhenEmailIsNotFound_ShouldThrowInvalidCredentialsException() {
        var loginRequest = new LoginRequest(EMAIL, PASSWORD);

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");

        verify(userAccountRepository).findByEmail(EMAIL);
        verify(customerInternalApi, never()).getCustomer(any());
        verify(passwordEncoder, never()).matches(any(), anyString());
    }

    @Test
    void login_WhenCustomerNotFound_ShouldThrowInvalidCredentialsException() {
        var userAccountEntity = createSavedUserAccount();
        var loginRequest = new LoginRequest(EMAIL, PASSWORD);
        when(userAccountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userAccountEntity));
        when(passwordEncoder.matches(PASSWORD, "encoded-password-hash")).thenReturn(true);
        when(customerInternalApi.getCustomer(CUSTOMER_ID)).thenThrow(new CustomerNotFoundException());

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");

        verify(userAccountRepository).findByEmail(EMAIL);
        var loginOrder = inOrder(passwordEncoder, customerInternalApi);
        loginOrder.verify(passwordEncoder).matches(PASSWORD, "encoded-password-hash");
        loginOrder.verify(customerInternalApi).getCustomer(CUSTOMER_ID);
    }

    @Test
    void login_WhenCustomerIsBlocked_ShouldThrowInvalidCredentialsException() {
        var userAccountEntity = createSavedUserAccount();
        var loginRequest = new LoginRequest(EMAIL, PASSWORD);
        when(userAccountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userAccountEntity));
        when(passwordEncoder.matches(PASSWORD, "encoded-password-hash")).thenReturn(true);
        when(customerInternalApi.getCustomer(CUSTOMER_ID)).thenReturn(CustomerResponse.builder()
                .id(CUSTOMER_ID)
                .email(EMAIL)
                .name(NAME)
                .status(CustomerStatus.BLOCKED)
                .build());

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");

        verify(userAccountRepository).findByEmail(EMAIL);
        var loginOrder = inOrder(passwordEncoder, customerInternalApi);
        loginOrder.verify(passwordEncoder).matches(PASSWORD, "encoded-password-hash");
        loginOrder.verify(customerInternalApi).getCustomer(CUSTOMER_ID);
    }

    @Test
    void login_WhenPasswordDoesNotMatch_ShouldThrowInvalidCredentialsException() {
        var userAccountEntity = createSavedUserAccount();
        var loginRequest = new LoginRequest(EMAIL, PASSWORD);
        when(userAccountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userAccountEntity));
        when(passwordEncoder.matches(PASSWORD, "encoded-password-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");

        verify(userAccountRepository).findByEmail(EMAIL);
        verifyNoInteractions(customerInternalApi);
    }

    @Test
    void login_WhenEmailDoesNotMatch_ShouldThrowInvalidCredentialsException() {
        var userAccountEntity = createSavedUserAccount();
        var loginRequest = new LoginRequest("customer2@example.com", PASSWORD);
        when(userAccountRepository.findByEmail("customer2@example.com")).thenReturn(Optional.of(userAccountEntity));
        when(passwordEncoder.matches(PASSWORD, "encoded-password-hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");

        verify(userAccountRepository).findByEmail("customer2@example.com");
        verifyNoInteractions(customerInternalApi);
        verify(passwordEncoder).matches(PASSWORD, "encoded-password-hash");
    }

    @Test
    void login_WhenCustomerLookupFailsUnexpectedly_ShouldPropagateOriginalFailure() {
        var savedUserAccount = createSavedUserAccount();
        var loginRequest = new LoginRequest(EMAIL, PASSWORD);
        var customerLookupFailure = new DataAccessResourceFailureException("Customer database is unavailable.");
        when(userAccountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(savedUserAccount));
        when(passwordEncoder.matches(PASSWORD, "encoded-password-hash")).thenReturn(true);
        when(customerInternalApi.getCustomer(CUSTOMER_ID)).thenThrow(customerLookupFailure);

        assertThatThrownBy(() -> authService.login(loginRequest)).isSameAs(customerLookupFailure);

        var loginOrder = inOrder(passwordEncoder, customerInternalApi);
        loginOrder.verify(passwordEncoder).matches(PASSWORD, "encoded-password-hash");
        loginOrder.verify(customerInternalApi).getCustomer(CUSTOMER_ID);
        verify(userAccountRepository, never()).save(any(UserAccountEntity.class));
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
