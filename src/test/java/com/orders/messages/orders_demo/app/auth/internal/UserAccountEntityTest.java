package com.orders.messages.orders_demo.app.auth.internal;

import java.util.HashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class UserAccountEntityTest {
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final String EMAIL = "customer@example.com";
    private static final String PASSWORD_HASH = "encoded-password-hash";

    @Test
    void build_WhenRequiredFieldsAreProvided_ShouldCreateCustomerAccountWithoutGeneratedFields() {
        var userAccount = createValidUserAccountBuilder().build();

        assertThat(userAccount.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(userAccount.getEmail()).isEqualTo(EMAIL);
        assertThat(userAccount.getPasswordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(userAccount.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(userAccount.getId()).isNull();
        assertThat(userAccount.getCreatedAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void build_WhenRoleIsProvided_ShouldPreserveRequestedRole(UserRole userRole) {
        UUID accountId = UUID.randomUUID();

        var userAccount = createValidUserAccountBuilder().id(accountId).role(userRole).build();

        assertThat(userAccount.getId()).isEqualTo(accountId);
        assertThat(userAccount.getRole()).isEqualTo(userRole);
    }

    @Test
    void build_WhenCustomerIdIsMissing_ShouldRejectAccount() {
        var userAccountBuilder = UserAccountEntity.builder().email(EMAIL).passwordHash(PASSWORD_HASH);

        assertThatThrownBy(userAccountBuilder::build)
                .isInstanceOf(IllegalStateException.class).hasMessage("Customer ID is required.");
    }

    @Test
    void build_WhenEmailIsMissing_ShouldRejectAccount() {
        var userAccountBuilder = UserAccountEntity.builder().customerId(CUSTOMER_ID).passwordHash(PASSWORD_HASH);

        assertThatThrownBy(userAccountBuilder::build)
                .isInstanceOf(IllegalStateException.class).hasMessage("Email is required.");
    }

    @Test
    void build_WhenPasswordHashIsMissing_ShouldRejectAccount() {
        var userAccountBuilder = UserAccountEntity.builder().customerId(CUSTOMER_ID).email(EMAIL);

        assertThatThrownBy(userAccountBuilder::build)
                .isInstanceOf(IllegalStateException.class).hasMessage("Password is required.");
    }

    @Test
    void id_WhenNull_ShouldRejectValue() {
        assertThatThrownBy(() -> createValidUserAccountBuilder().id(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void customerId_WhenNull_ShouldRejectValue() {
        assertThatThrownBy(() -> createValidUserAccountBuilder().customerId(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void email_WhenNull_ShouldRejectValue() {
        assertThatThrownBy(() -> createValidUserAccountBuilder().email(null)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "\t\n" })
    void build_WhenEmailIsBlank_ShouldRejectAccount(String blankEmail) {
        assertThatThrownBy(() -> createValidUserAccountBuilder().email(blankEmail).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void passwordHash_WhenNull_ShouldRejectValue() {
        assertThatThrownBy(() -> createValidUserAccountBuilder().passwordHash(null))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "\t\n" })
    void build_WhenPasswordHashIsBlank_ShouldRejectAccount(String blankPasswordHash) {
        assertThatThrownBy(() -> createValidUserAccountBuilder().passwordHash(blankPasswordHash).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void role_WhenNull_ShouldRejectValue() {
        assertThatThrownBy(() -> createValidUserAccountBuilder().role(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void equals_WhenComparedWithItselfWithoutId_ShouldReturnTrue() {
        var userAccount = createValidUserAccountBuilder().build();

        assertThat(userAccount.equals(userAccount)).isTrue();
    }

    @Test
    void equals_WhenDifferentAccountsHaveNoId_ShouldReturnFalseInBothDirections() {
        var firstUserAccount = createValidUserAccountBuilder().build();
        var secondUserAccount = createValidUserAccountBuilder().build();

        assertThat(firstUserAccount.equals(secondUserAccount)).isFalse();
        assertThat(secondUserAccount.equals(firstUserAccount)).isFalse();
    }

    @Test
    void equals_WhenAccountsHaveSameIdAndDifferentFields_ShouldReturnTrueInBothDirections() {
        UUID accountId = UUID.randomUUID();
        var firstUserAccount = createValidUserAccountBuilder().id(accountId).build();
        var secondUserAccount = UserAccountEntity.builder().id(accountId).customerId(UUID.randomUUID())
                .email("other@example.com").passwordHash("other-encoded-password").role(UserRole.ADMIN).build();

        assertThat(firstUserAccount.equals(secondUserAccount)).isTrue();
        assertThat(secondUserAccount.equals(firstUserAccount)).isTrue();
        assertThat(firstUserAccount.hashCode()).isEqualTo(secondUserAccount.hashCode());
    }

    @Test
    void equals_WhenAccountsHaveDifferentIds_ShouldReturnFalse() {
        var firstUserAccount = createValidUserAccountBuilder().id(UUID.randomUUID()).build();
        var secondUserAccount = createValidUserAccountBuilder().id(UUID.randomUUID()).build();

        assertThat(firstUserAccount.equals(secondUserAccount)).isFalse();
    }

    @Test
    void equals_WhenOnlyOneAccountHasId_ShouldReturnFalseInBothDirections() {
        var persistedUserAccount = createValidUserAccountBuilder().id(UUID.randomUUID()).build();
        var newUserAccount = createValidUserAccountBuilder().build();

        assertThat(persistedUserAccount.equals(newUserAccount)).isFalse();
        assertThat(newUserAccount.equals(persistedUserAccount)).isFalse();
    }

    @Test
    void equals_WhenComparedWithNullOrDifferentType_ShouldReturnFalse() {
        var userAccount = createValidUserAccountBuilder().build();

        assertThat(userAccount.equals(null)).isFalse();
        assertThat(userAccount.equals(EMAIL)).isFalse();
    }

    @Test
    void hashCode_WhenGeneratedIdIsAssigned_ShouldRemainStableAndKeepAccountInHashSet() {
        var userAccount = createValidUserAccountBuilder().build();
        int hashCodeBeforePersistence = userAccount.hashCode();
        var userAccounts = new HashSet<UserAccountEntity>();
        userAccounts.add(userAccount);

        // Simulate JPA assigning the generated ID without starting a database.
        ReflectionTestUtils.setField(userAccount, "id", UUID.randomUUID());

        assertThat(userAccount.hashCode()).isEqualTo(hashCodeBeforePersistence);
        assertThat(userAccounts.contains(userAccount)).isTrue();
    }

    private UserAccountEntity.Builder createValidUserAccountBuilder() {
        return UserAccountEntity.builder().customerId(CUSTOMER_ID).email(EMAIL).passwordHash(PASSWORD_HASH);
    }
}
