package com.orders.messages.orders_demo.app.auth.internal;

import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;

@Transactional
class AuthRepositoryIntegrationTest extends PostgresContainer {
    @Autowired private UserAccountRepository accountRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AccessTokenRepository tokenRepository;

    @Test
    void shouldPersistAccountAndFindItByNormalizedEmailAfterClearingContext() {
        var userAccount = accountRepository.saveAndFlush(createAccount(" CUSTOMER@EXAMPLE.COM ", persistCustomer()));
        UUID accountId = userAccount.getId();
        entityManager.clear();

        var savedAccount = accountRepository.findByEmail("customer@example.com").orElseThrow();

        assertThat(savedAccount.getId()).isEqualTo(accountId);
        assertThat(savedAccount.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(savedAccount.getCreatedAt()).isNotNull();
        assertThat(accountRepository.existsByEmail("customer@example.com")).isTrue();
        assertThat(accountRepository.findByEmail("missing@example.com")).isEmpty();
    }

    @Test
    void shouldRejectDuplicateNormalizedEmailInDatabase() {
        accountRepository.saveAndFlush(createAccount("customer@example.com", persistCustomer()));
        var duplicateAccount = createAccount("CUSTOMER@EXAMPLE.COM", persistCustomer());

        assertThatThrownBy(() -> accountRepository.saveAndFlush(duplicateAccount))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectTwoAccountsLinkedToSameCustomer() {
        UUID customerId = persistCustomer();
        accountRepository.saveAndFlush(createAccount("first@example.com", customerId));
        var duplicateCustomerAccount = createAccount("second@example.com", customerId);

        assertThatThrownBy(() -> accountRepository.saveAndFlush(duplicateCustomerAccount))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldPersistTokenHashAndDeleteOnlyRequestedToken() {
        var account = accountRepository.saveAndFlush(createAccount("customer@example.com", persistCustomer()));
        String firstTokenHash = AuthService.hashToken("first-token");
        String secondTokenHash = AuthService.hashToken("second-token");
        Instant expiresAt = Instant.parse("2030-01-01T12:00:00Z");
        tokenRepository.saveAndFlush(new AccessTokenEntity(firstTokenHash, account.getId(), expiresAt));
        tokenRepository.saveAndFlush(new AccessTokenEntity(secondTokenHash, account.getId(), expiresAt));
        entityManager.clear();

        var savedToken = tokenRepository.findByTokenHash(firstTokenHash).orElseThrow();
        assertThat(savedToken.getAccountId()).isEqualTo(account.getId());
        assertThat(savedToken.getExpiresAt()).isEqualTo(expiresAt);
        tokenRepository.deleteByTokenHash(firstTokenHash);
        tokenRepository.flush();
        entityManager.clear();

        assertThat(tokenRepository.findByTokenHash(firstTokenHash)).isEmpty();
        assertThat(tokenRepository.findByTokenHash(secondTokenHash)).isPresent();
    }

    @Test
    void shouldRejectDuplicateTokenHashInDatabase() {
        var account = accountRepository.saveAndFlush(createAccount("customer@example.com", persistCustomer()));
        String tokenHash = AuthService.hashToken("same-token");
        Instant expiresAt = Instant.parse("2030-01-01T12:00:00Z");
        tokenRepository.saveAndFlush(new AccessTokenEntity(tokenHash, account.getId(), expiresAt));

        assertThatThrownBy(() -> tokenRepository.saveAndFlush(new AccessTokenEntity(tokenHash, account.getId(), expiresAt)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID persistCustomer() {
        return customerRepository.saveAndFlush(new CustomerEntity(UUID.randomUUID() + "@example.com", "Customer")).getId();
    }

    private UserAccountEntity createAccount(String email, UUID customerId) {
        return UserAccountEntity.builder().customerId(customerId).email(email).passwordHash("encoded-password").build();
    }
}
