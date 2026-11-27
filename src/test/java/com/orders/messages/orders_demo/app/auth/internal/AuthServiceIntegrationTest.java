package com.orders.messages.orders_demo.app.auth.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.auth.api.LoginRequest;
import com.orders.messages.orders_demo.app.auth.api.RegisterRequest;
import com.orders.messages.orders_demo.app.auth.internal.exceptions.InvalidCredentialsException;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.customer.internal.CustomerStatus;

class AuthServiceIntegrationTest extends PostgresContainer {

    @Autowired
    private AuthService authService;
    @MockitoSpyBean
    private UserAccountRepository userAccountRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String PASSWORD = "a-long-test-password";
    private static final String NAME = "Test Customer";

    private final List<UUID> accountIds = new ArrayList<>();
    private final List<UUID> customerIds = new ArrayList<>();

    private String email;
    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        email = UUID.randomUUID() + "@example.com";
        registerRequest = new RegisterRequest(email, PASSWORD, NAME);
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            accountIds.forEach(userAccountRepository::deleteById);
            userAccountRepository.flush();
            customerIds.forEach(customerRepository::deleteById);
            customerRepository.flush();
        });
        accountIds.clear();
        customerIds.clear();
    }

    /*
     * REGISTER
     */

    @Test
    void register_WhenRequestIsValid_ShouldPersistAccountLinkedToNewCustomer() {

        var userAccountResponse = authService.register(registerRequest);
        accountIds.add(userAccountResponse.id());

        transactionTemplate.executeWithoutResult(status -> {
            var persistedUserAccount = userAccountRepository.findById(userAccountResponse.id()).orElseThrow();
            customerIds.add(persistedUserAccount.getCustomerId());
            var persistedCustomer = customerRepository.findById(persistedUserAccount.getCustomerId()).orElseThrow();

            assertThat(persistedUserAccount.getEmail()).isEqualTo(email);
            assertThat(persistedUserAccount.getRole()).isEqualTo(UserRole.CUSTOMER);
            assertThat(persistedUserAccount.getCreatedAt()).isNotNull();
            assertThat(persistedCustomer.getEmail()).isEqualTo(email);
            assertThat(persistedCustomer.getName()).isEqualTo(NAME);
            assertThat(userAccountResponse.email()).isEqualTo(email);
            assertThat(userAccountResponse.role()).isEqualTo(UserRole.CUSTOMER);
            assertThat(userAccountRepository.existsByEmail(email)).isTrue();
            assertThat(customerRepository.existsByEmail(email)).isTrue();
        });
    }

    @Test
    void register_WhenEmailAlreadyBelongsToCustomer_ShouldRejectWithoutCreatingAccountOrChangingCustomer() {
        var existingCustomer = customerRepository.saveAndFlush(new CustomerEntity(email, "Existing Customer"));
        customerIds.add(existingCustomer.getId());

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(DataIntegrityViolationException.class);

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(userAccountRepository.existsByEmail(email)).isFalse();
            assertThat(customerRepository.existsByEmail(email)).isTrue();
            var unchangedCustomer = customerRepository.findById(existingCustomer.getId()).orElseThrow();
            assertThat(unchangedCustomer.getEmail()).isEqualTo(email);
            assertThat(unchangedCustomer.getName()).isEqualTo("Existing Customer");
            assertThat(unchangedCustomer.getStatus()).isEqualTo(existingCustomer.getStatus());
        });
    }

    @Test
    void register_WhenAccountSaveFailsAfterFlush_ShouldRollBackAccountAndCustomer() {
        var accountSaveFailure = new IllegalStateException("Account save failed after flush.");
        var originalRepositoryAnswer = mockingDetails(userAccountRepository).getMockCreationSettings()
                .getDefaultAnswer();
        doAnswer(invocation -> {
            UserAccountEntity savedUserAccount = (UserAccountEntity) originalRepositoryAnswer.answer(invocation);
            entityManager.flush();
            accountIds.add(savedUserAccount.getId());
            customerIds.add(savedUserAccount.getCustomerId());
            throw accountSaveFailure;
        }).when(userAccountRepository).save(any(UserAccountEntity.class));

        assertThatThrownBy(() -> authService.register(registerRequest)).isSameAs(accountSaveFailure);

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(userAccountRepository.existsByEmail(email)).isFalse();
            assertThat(customerRepository.existsByEmail(email)).isFalse();
        });
    }

    @Test
    void register_WhenCallerTransactionFails_ShouldRollBackAccountAndCustomer() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            var userAccountResponse = authService.register(registerRequest);
            entityManager.flush();
            var savedUserAccount = userAccountRepository.findById(userAccountResponse.id()).orElseThrow();
            accountIds.add(savedUserAccount.getId());
            customerIds.add(savedUserAccount.getCustomerId());
            throw new IllegalStateException("Caller transaction failed.");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Caller transaction failed.");

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(userAccountRepository.existsByEmail(email)).isFalse();
            assertThat(customerRepository.existsByEmail(email)).isFalse();
        });
    }

    /*
     * LOG IN
     */

    @Test
    void login_WhenCredentialsAreValid_ShouldReturnExistingAccountWithoutChangingIt() {
        var loginRequest = new LoginRequest(email, PASSWORD);
        var initialUser = persistUser();

        var response = authService.login(loginRequest);

        assertThat(response.id()).isEqualTo(initialUser.getId());
        assertThat(response.email()).isEqualTo(email);
        assertThat(response.role()).isEqualTo(UserRole.CUSTOMER);
        assertUserAndCustomerDidNotChange(initialUser, CustomerStatus.ACTIVE);
    }

    @Test
    void login_WhenEmailDoesNotExist_ShouldRejectWithInvalidCredentials() {
        var loginRequest = new LoginRequest(email, PASSWORD);

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(userAccountRepository.existsByEmail(email)).isFalse();
            assertThat(customerRepository.existsByEmail(email)).isFalse();
        });
    }

    @Test
    void login_WhenPasswordIsIncorrect_ShouldRejectWithoutChangingAccountOrCustomer() {
        var loginRequest = new LoginRequest(email, "wrong_password");
        var initialUser = persistUser();

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");

        assertUserAndCustomerDidNotChange(initialUser, CustomerStatus.ACTIVE);
    }

    @Test
    void login_WhenCustomerIsBlocked_ShouldRejectWithoutChangingAccountOrCustomer() {
        var loginRequest = new LoginRequest(email, PASSWORD);
        var initialUser = persistUser(CustomerStatus.BLOCKED);

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");

        assertUserAndCustomerDidNotChange(initialUser, CustomerStatus.BLOCKED);
    }

    @Test
    void login_WhenAssociatedCustomerDoesNotExist_ShouldRejectWithoutChangingAccount() {
        UUID nonExistentId = UUID.randomUUID();
        var loginRequest = new LoginRequest(email, PASSWORD);
        var account = userAccountRepository.saveAndFlush(UserAccountEntity.builder()
                .customerId(nonExistentId)
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .build());
        accountIds.add(account.getId());
        entityManager.clear();

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");

        transactionTemplate.executeWithoutResult(status -> {
            var persistedUser = userAccountRepository.findById(account.getId()).orElseThrow();

            assertThat(customerRepository.existsById(nonExistentId)).isFalse();
            assertThat(persistedUser).usingRecursiveComparison().isEqualTo(account);
        });
    }

    private void assertUserAndCustomerDidNotChange(
            UserAccountEntity initialUser,
            CustomerStatus initialCustomerStatus) {
        transactionTemplate.executeWithoutResult(status -> {
            var persistedUser = userAccountRepository.findById(initialUser.getId()).orElseThrow();
            var persistedCustomer = customerRepository.findById(initialUser.getCustomerId()).orElseThrow();

            assertThat(persistedUser).usingRecursiveComparison().isEqualTo(initialUser);
            assertThat(persistedCustomer.getName()).isEqualTo(NAME);
            assertThat(persistedCustomer.getEmail()).isEqualTo(email);
            assertThat(persistedCustomer.getStatus()).isEqualTo(initialCustomerStatus);
        });
    }

    private UserAccountEntity persistUser() {
        return transactionTemplate.execute(action -> {

            UUID customerId = customerRepository.saveAndFlush(new CustomerEntity(email, NAME)).getId();
            customerIds.add(customerId);

            var account = userAccountRepository.saveAndFlush(UserAccountEntity.builder()
                    .customerId(customerId)
                    .email(email)
                    .passwordHash(passwordEncoder.encode(PASSWORD))
                    .build());
            accountIds.add(account.getId());

            return account;

        });
    }

    private UserAccountEntity persistUser(CustomerStatus status) {
        return transactionTemplate.execute(action -> {

            UUID customerId = customerRepository.saveAndFlush(new CustomerEntity(email, NAME, status)).getId();
            customerIds.add(customerId);

            var account = userAccountRepository.saveAndFlush(UserAccountEntity.builder()
                    .customerId(customerId)
                    .email(email)
                    .passwordHash(passwordEncoder.encode(PASSWORD))
                    .build());
            accountIds.add(account.getId());

            return account;

        });
    }
}
