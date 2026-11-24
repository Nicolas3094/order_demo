package com.orders.messages.orders_demo.app.auth.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.orders.messages.orders_demo.app.auth.api.LoginRequest;
import com.orders.messages.orders_demo.app.auth.api.RegisterRequest;
import com.orders.messages.orders_demo.app.auth.internal.exceptions.EmailAlreadyRegisteredException;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.customer.internal.CustomerStatus;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final String EMAIL = "customer@example.com";
    private static final String PASSWORD = "a-long-test-password";
    private static final String RAW_TOKEN = "a".repeat(43);
    @Mock private UserAccountRepository accountRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private AccessTokenRepository tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode("unused-dummy-password")).thenReturn("dummy-hash");
        authService = new AuthService(accountRepository, customerRepository, tokenRepository,
                passwordEncoder, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(30));
    }

    @Test
    void register_WhenValid_ShouldNormalizeEmailHashPasswordAndCreateCustomerAccount() {
        var registerRequest = new RegisterRequest("  CUSTOMER@EXAMPLE.COM  ", PASSWORD, " Customer ");
        when(customerRepository.saveAndFlush(any(CustomerEntity.class)))
                .thenReturn(new CustomerEntity(CUSTOMER_ID, EMAIL, "Customer", CustomerStatus.ACTIVE));
        when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded-password");
        when(accountRepository.saveAndFlush(any(UserAccountEntity.class))).thenAnswer(invocation -> {
            UserAccountEntity savedAccount = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedAccount, "id", ACCOUNT_ID);
            return savedAccount;
        });

        var accountResponse = authService.register(registerRequest);

        var accountCaptor = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(accountRepository).saveAndFlush(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getPasswordHash()).isEqualTo("encoded-password");
        assertThat(accountCaptor.getValue().getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(accountResponse.id()).isEqualTo(ACCOUNT_ID);
        assertThat(accountResponse.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(accountResponse.email()).isEqualTo(EMAIL);
        var customerCaptor = ArgumentCaptor.forClass(CustomerEntity.class);
        verify(customerRepository).saveAndFlush(customerCaptor.capture());
        assertThat(customerCaptor.getValue().getName()).isEqualTo("Customer");
        assertThat(customerCaptor.getValue().getEmail()).isEqualTo(EMAIL);
        verifyNoInteractions(tokenRepository);
    }

    @Test
    void register_WhenAccountEmailExists_ShouldRejectWithoutCreatingCustomer() {
        when(accountRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest(EMAIL, PASSWORD, "Customer")))
                .isInstanceOf(EmailAlreadyRegisteredException.class).hasMessage("Email is already registered.");

        verifyNoInteractions(customerRepository, tokenRepository);
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_WhenCustomerEmailExists_ShouldRejectWithoutLinkingExistingCustomer() {
        when(customerRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest(EMAIL, PASSWORD, "Customer")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(customerRepository, never()).saveAndFlush(any());
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void login_WhenCredentialsAreValid_ShouldReturnRandomBearerAndStoreOnlyItsHash() {
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(createAccount()));
        when(passwordEncoder.matches(PASSWORD, "encoded-password")).thenReturn(true);
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(createCustomer(CustomerStatus.ACTIVE)));

        var authResponse = authService.login(new LoginRequest(" CUSTOMER@EXAMPLE.COM ", PASSWORD));

        assertThat(authResponse.accessToken()).matches("[A-Za-z0-9_-]{43}");
        assertThat(authResponse.tokenType()).isEqualTo("Bearer");
        assertThat(authResponse.expiresAt()).isEqualTo(NOW.plusSeconds(1800));
        assertThat(authResponse.account().id()).isEqualTo(ACCOUNT_ID);
        var tokenCaptor = ArgumentCaptor.forClass(AccessTokenEntity.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getTokenHash()).isEqualTo(AuthService.hashToken(authResponse.accessToken()));
        assertThat(tokenCaptor.getValue().getTokenHash()).isNotEqualTo(authResponse.accessToken());
        assertThat(tokenCaptor.getValue().getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(tokenCaptor.getValue().getExpiresAt()).isEqualTo(authResponse.expiresAt());
    }

    @Test
    void login_WhenAccountIsMissing_ShouldCheckDummyHashAndRejectWithoutToken() {
        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD)))
                .isInstanceOf(BadCredentialsException.class).hasMessage("Invalid email or password.");

        verify(passwordEncoder).matches(PASSWORD, "dummy-hash");
        verifyNoInteractions(tokenRepository, customerRepository);
    }

    @Test
    void login_WhenPasswordIsWrong_ShouldRejectWithoutToken() {
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(createAccount()));

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "incorrect-password")))
                .isInstanceOf(BadCredentialsException.class).hasMessage("Invalid email or password.");

        verifyNoInteractions(tokenRepository, customerRepository);
    }

    @Test
    void login_WhenCustomerIsBlocked_ShouldRejectWithoutToken() {
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(createAccount()));
        when(passwordEncoder.matches(PASSWORD, "encoded-password")).thenReturn(true);
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(createCustomer(CustomerStatus.BLOCKED)));

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD)))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(tokenRepository);
    }

    @Test
    void authenticate_WhenTokenIsValid_ShouldLoadCurrentAccountAndCustomer() {
        when(tokenRepository.findByTokenHash(AuthService.hashToken(RAW_TOKEN)))
                .thenReturn(Optional.of(new AccessTokenEntity(AuthService.hashToken(RAW_TOKEN), ACCOUNT_ID, NOW.plusSeconds(1))));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(createAccount()));
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(createCustomer(CustomerStatus.ACTIVE)));

        var authenticatedUser = authService.authenticate(RAW_TOKEN);

        assertThat(authenticatedUser.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(authenticatedUser.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(authenticatedUser.role()).isEqualTo(UserRole.CUSTOMER);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "short", " ", "not.a.jwt", "../../token" })
    void authenticate_WhenTokenIsMalformed_ShouldRejectBeforeDatabaseLookup(String malformedToken) {
        assertThatThrownBy(() -> authService.authenticate(malformedToken)).isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(tokenRepository, accountRepository, customerRepository);
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, -1 })
    void authenticate_WhenTokenIsExpired_ShouldRejectAtExactBoundary(long expirationOffset) {
        when(tokenRepository.findByTokenHash(AuthService.hashToken(RAW_TOKEN)))
                .thenReturn(Optional.of(new AccessTokenEntity(AuthService.hashToken(RAW_TOKEN), ACCOUNT_ID,
                        NOW.plusSeconds(expirationOffset))));

        assertThatThrownBy(() -> authService.authenticate(RAW_TOKEN)).isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(accountRepository, customerRepository);
    }

    @Test
    void authenticate_WhenTokenIsMissing_ShouldReject() {
        assertThatThrownBy(() -> authService.authenticate(RAW_TOKEN)).isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(accountRepository, customerRepository);
    }

    @Test
    void authenticate_WhenAccountWasDeleted_ShouldReject() {
        when(tokenRepository.findByTokenHash(AuthService.hashToken(RAW_TOKEN)))
                .thenReturn(Optional.of(new AccessTokenEntity(AuthService.hashToken(RAW_TOKEN), ACCOUNT_ID, NOW.plusSeconds(1))));

        assertThatThrownBy(() -> authService.authenticate(RAW_TOKEN)).isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(customerRepository);
    }

    @Test
    void authenticate_WhenCustomerWasBlockedAfterLogin_ShouldReject() {
        when(tokenRepository.findByTokenHash(AuthService.hashToken(RAW_TOKEN)))
                .thenReturn(Optional.of(new AccessTokenEntity(AuthService.hashToken(RAW_TOKEN), ACCOUNT_ID, NOW.plusSeconds(1))));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(createAccount()));
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(createCustomer(CustomerStatus.BLOCKED)));

        assertThatThrownBy(() -> authService.authenticate(RAW_TOKEN)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void logout_ShouldDeleteOnlyHashOfPresentedToken() {
        authService.logout(RAW_TOKEN);

        verify(tokenRepository).deleteByTokenHash(AuthService.hashToken(RAW_TOKEN));
        verifyNoInteractions(accountRepository, customerRepository);
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, -1 })
    void constructor_WhenTokenLifetimeIsNotPositive_ShouldReject(long lifetimeSeconds) {
        assertThatThrownBy(() -> new AuthService(accountRepository, customerRepository, tokenRepository,
                passwordEncoder, Clock.systemUTC(), Duration.ofSeconds(lifetimeSeconds)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Token lifetime must be positive.");
    }

    private UserAccountEntity createAccount() {
        return UserAccountEntity.builder().id(ACCOUNT_ID).customerId(CUSTOMER_ID).email(EMAIL)
                .passwordHash("encoded-password").build();
    }

    private CustomerEntity createCustomer(CustomerStatus customerStatus) {
        return new CustomerEntity(CUSTOMER_ID, EMAIL, "Customer", customerStatus);
    }
}
