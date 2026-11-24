package com.orders.messages.orders_demo.app.repositories;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.auth.internal.UserAccountEntity;
import com.orders.messages.orders_demo.app.auth.internal.UserAccountRepository;
import com.orders.messages.orders_demo.app.auth.internal.UserRole;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;

@Transactional
class UserAccountRepositoryIntegrationTest extends PostgresContainer {
    private static final String PASSWORD_HASH = "encoded-password-hash";

    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldPersistAndReloadAccountWithAllMappedFieldsAndGeneratedValues() {
        UUID customerId = persistCustomer();
        String email = UUID.randomUUID() + "@example.com";
        var savedUserAccount = userAccountRepository.saveAndFlush(UserAccountEntity.builder()
                .customerId(customerId).email(email).passwordHash(PASSWORD_HASH).build());
        UUID accountId = savedUserAccount.getId();
        entityManager.clear();

        var reloadedUserAccount = userAccountRepository.findById(accountId).orElseThrow();

        assertThat(reloadedUserAccount).isNotSameAs(savedUserAccount);
        assertThat(reloadedUserAccount.getId()).isNotNull().isEqualTo(accountId);
        assertThat(reloadedUserAccount.getCustomerId()).isEqualTo(customerId);
        assertThat(reloadedUserAccount.getEmail()).isEqualTo(email);
        assertThat(reloadedUserAccount.getPasswordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(reloadedUserAccount.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(reloadedUserAccount.getCreatedAt()).isNotNull().isEqualTo(savedUserAccount.getCreatedAt());
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void shouldPersistAndReloadEachRoleAsItsEnumName(UserRole userRole) {
        var savedUserAccount = userAccountRepository.saveAndFlush(createUserAccountBuilder().role(userRole).build());
        UUID accountId = savedUserAccount.getId();
        entityManager.clear();

        var reloadedUserAccount = userAccountRepository.findById(accountId).orElseThrow();
        String storedRole = jdbcTemplate.queryForObject(
                "SELECT role FROM user_account WHERE id = ?", String.class, accountId);

        assertThat(reloadedUserAccount.getRole()).isEqualTo(userRole);
        assertThat(storedRole).isEqualTo(userRole.name());
    }

    @Test
    void shouldFindOnlyAccountMatchingRequestedEmail() {
        var expectedUserAccount = userAccountRepository.saveAndFlush(createUserAccountBuilder().build());
        userAccountRepository.saveAndFlush(createUserAccountBuilder().build());
        String requestedEmail = expectedUserAccount.getEmail();
        entityManager.clear();

        var foundUserAccount = userAccountRepository.findByEmail(requestedEmail).orElseThrow();

        assertThat(foundUserAccount.getId()).isEqualTo(expectedUserAccount.getId());
        assertThat(foundUserAccount.getEmail()).isEqualTo(requestedEmail);
        assertThat(foundUserAccount).isNotSameAs(expectedUserAccount);
    }

    @Test
    void shouldReturnEmptyWhenEmailDoesNotExist() {
        userAccountRepository.saveAndFlush(createUserAccountBuilder().build());
        entityManager.clear();

        var missingUserAccount = userAccountRepository.findByEmail("missing@example.com");

        assertThat(missingUserAccount).isEmpty();
    }

    @Test
    void shouldReturnTrueWhenEmailExists() {
        var savedUserAccount = userAccountRepository.saveAndFlush(createUserAccountBuilder().build());
        entityManager.clear();

        boolean emailExists = userAccountRepository.existsByEmail(savedUserAccount.getEmail());

        assertThat(emailExists).isTrue();
    }

    @Test
    void shouldReturnFalseWhenEmailDoesNotExist() {
        userAccountRepository.saveAndFlush(createUserAccountBuilder().build());
        entityManager.clear();

        boolean emailExists = userAccountRepository.existsByEmail("missing@example.com");

        assertThat(emailExists).isFalse();
    }

    @Test
    void shouldRejectDuplicateEmailForDifferentCustomers() {
        String repeatedEmail = UUID.randomUUID() + "@example.com";
        userAccountRepository.saveAndFlush(createUserAccountBuilder().email(repeatedEmail).build());
        var duplicateEmailAccount = createUserAccountBuilder().email(repeatedEmail).build();
        entityManager.clear();

        assertThatThrownBy(() -> userAccountRepository.saveAndFlush(duplicateEmailAccount))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("uk_email");
    }

    // JDBC bypasses builder and Hibernate validation to exercise PostgreSQL NOT NULL constraints.
    @Test
    void shouldRejectNullCustomerIdInDatabase() {
        UUID accountId = userAccountRepository.saveAndFlush(createUserAccountBuilder().build()).getId();
        entityManager.clear();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE user_account SET customer_id = NULL WHERE id = ?", accountId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("null value in column \"customer_id\"");
    }

    @Test
    void shouldRejectNullEmailInDatabase() {
        UUID accountId = userAccountRepository.saveAndFlush(createUserAccountBuilder().build()).getId();
        entityManager.clear();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE user_account SET email = NULL WHERE id = ?", accountId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("null value in column \"email\"");
    }

    @Test
    void shouldRejectNullPasswordHashInDatabase() {
        UUID accountId = userAccountRepository.saveAndFlush(createUserAccountBuilder().build()).getId();
        entityManager.clear();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE user_account SET password_hash = NULL WHERE id = ?", accountId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("null value in column \"password_hash\"");
    }

    @Test
    void shouldRejectNullRoleInDatabase() {
        UUID accountId = userAccountRepository.saveAndFlush(createUserAccountBuilder().build()).getId();
        entityManager.clear();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE user_account SET role = NULL WHERE id = ?", accountId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("null value in column \"role\"");
    }

    @Test
    void shouldPersistEmailAndPasswordHashAtMaximumColumnLength() {
        String maximumLengthEmail = "e".repeat(243) + "@example.com";
        String maximumLengthPasswordHash = "h".repeat(255);
        var savedUserAccount = userAccountRepository.saveAndFlush(createUserAccountBuilder()
                .email(maximumLengthEmail).passwordHash(maximumLengthPasswordHash).build());
        UUID accountId = savedUserAccount.getId();
        entityManager.clear();

        var reloadedUserAccount = userAccountRepository.findById(accountId).orElseThrow();

        assertThat(reloadedUserAccount.getEmail()).hasSize(255).isEqualTo(maximumLengthEmail);
        assertThat(reloadedUserAccount.getPasswordHash()).hasSize(255).isEqualTo(maximumLengthPasswordHash);
    }

    @Test
    void shouldRejectEmailExceedingColumnLength() {
        var oversizedEmailAccount = createUserAccountBuilder().email("e".repeat(244) + "@example.com").build();

        assertThatThrownBy(() -> userAccountRepository.saveAndFlush(oversizedEmailAccount))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("value too long for type character varying(255)");
    }

    @Test
    void shouldRejectPasswordHashExceedingColumnLength() {
        var oversizedPasswordHashAccount = createUserAccountBuilder().passwordHash("h".repeat(256)).build();

        assertThatThrownBy(() -> userAccountRepository.saveAndFlush(oversizedPasswordHashAccount))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("value too long for type character varying(255)");
    }

    private UUID persistCustomer() {
        return customerRepository.saveAndFlush(
                new CustomerEntity(UUID.randomUUID() + "@example.com", "Test Customer")).getId();
    }

    private UserAccountEntity.Builder createUserAccountBuilder() {
        return UserAccountEntity.builder().customerId(persistCustomer())
                .email(UUID.randomUUID() + "@example.com").passwordHash(PASSWORD_HASH);
    }
}
