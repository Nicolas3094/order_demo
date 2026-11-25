package com.orders.messages.orders_demo.app.auth.internal;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.auth.api.RegisterRequest;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;

class AuthServiceIntegrationTest extends PostgresContainer {
    private static final String PASSWORD = "a-long-test-password";
    private static final String NAME = "Test Customer";

    @Autowired
    private AuthService authService;
    @MockitoSpyBean
    private UserAccountRepository userAccountRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

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
            jdbcTemplate.update("DELETE FROM user_account WHERE email = ?", email);
            jdbcTemplate.update("DELETE FROM customer WHERE email = ?", email);
        });
    }

    @Test
    void register_WhenRequestIsValid_ShouldPersistAccountLinkedToNewCustomer() {
        var userAccountResponse = authService.register(registerRequest);

        transactionTemplate.executeWithoutResult(status -> {
            entityManager.clear();
            var persistedUserAccount = userAccountRepository.findById(userAccountResponse.id()).orElseThrow();
            var persistedCustomer = customerRepository.findById(persistedUserAccount.getCustomerId()).orElseThrow();

            assertThat(persistedUserAccount.getEmail()).isEqualTo(email);
            assertThat(persistedUserAccount.getRole()).isEqualTo(UserRole.CUSTOMER);
            assertThat(persistedUserAccount.getCreatedAt()).isNotNull();
            assertThat(persistedCustomer.getEmail()).isEqualTo(email);
            assertThat(persistedCustomer.getName()).isEqualTo(NAME);
            assertThat(userAccountResponse.email()).isEqualTo(email);
            assertThat(userAccountResponse.role()).isEqualTo(UserRole.CUSTOMER);
        });
        assertThat(countCustomersWithRegistrationEmail()).isEqualTo(1L);
    }

    @Test
    void register_WhenCustomerCannotBeCreated_ShouldNotPersistAccountOrChangeExistingCustomer() {
        var existingCustomer = customerRepository.saveAndFlush(new CustomerEntity(email, "Existing Customer"));

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(userAccountRepository.findByEmail(email)).isEmpty();
        assertThat(countCustomersWithRegistrationEmail()).isEqualTo(1L);
        assertThat(customerRepository.findById(existingCustomer.getId()).orElseThrow().getName())
                .isEqualTo("Existing Customer");
    }

    @Test
    void register_WhenAccountSaveFailsAfterFlush_ShouldRollBackAccountAndCustomer() {
        var accountSaveFailure = new IllegalStateException("Account save failed after flush.");
        var originalRepositoryAnswer = mockingDetails(userAccountRepository).getMockCreationSettings()
                .getDefaultAnswer();
        doAnswer(invocation -> {
            originalRepositoryAnswer.answer(invocation);
            entityManager.flush();
            throw accountSaveFailure;
        }).when(userAccountRepository).save(any(UserAccountEntity.class));

        assertThatThrownBy(() -> authService.register(registerRequest)).isSameAs(accountSaveFailure);

        assertThat(userAccountRepository.findByEmail(email)).isEmpty();
        assertThat(countCustomersWithRegistrationEmail()).isZero();
    }

    @Test
    void register_WhenCallerTransactionFails_ShouldRollBackAccountAndCustomer() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            authService.register(registerRequest);
            entityManager.flush();
            throw new IllegalStateException("Caller transaction failed.");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Caller transaction failed.");

        assertThat(countCustomersWithRegistrationEmail()).isZero();
    }

    private long countCustomersWithRegistrationEmail() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customer WHERE email = ?", Long.class, email);
    }
}
