package com.orders.messages.orders_demo.app.auth.internal;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.auth.api.RegisterRequest;
import com.orders.messages.orders_demo.app.auth.api.UserAccountResponse;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.customer.internal.CustomerStatus;

@AutoConfigureMockMvc
public class AuthControllerIntegrationTest extends PostgresContainer {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final List<UUID> accountIds = new ArrayList<>();
    private final List<UUID> customerIds = new ArrayList<>();

    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    public void afterEach() {
        transactionTemplate.executeWithoutResult((action) -> {
            accountIds.forEach(userAccountRepository::deleteById);
            customerIds.forEach(customerRepository::deleteById);
            userAccountRepository.flush();
            customerRepository.flush();
        });
        accountIds.clear();
        customerIds.clear();
    }

    @Test
    public void shouldRegisterAccountWithRequestAndPersistAccount() throws Exception {
        String userEmail = "user_email@email.com";
        String userPassword = "user_password";
        String userName = "user_name";
        RegisterRequest request = new RegisterRequest(userEmail, userPassword, userName);

        MvcResult mvcResult = performRegister(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath(".id").exists())
                .andExpect(jsonPath(".email").value(userEmail))
                .andExpect(jsonPath(".role").value("CUSTOMER"))
                .andReturn();
        var response = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), UserAccountResponse.class);
        accountIds.add(response.id());

        assertAccountCustomerPersistence(response.id(), userEmail, userPassword, userName);
    }

    @Test
    public void shouldRejectRegisterAccountWhenEmailIsDuplicatedAndPersistUser() throws Exception {
        String userEmail = "user_email@email.com";
        String userPassword = "user_password";
        String userName = "user_name";
        RegisterRequest request = new RegisterRequest(userEmail, userPassword, userName);
        RegisterRequest secondRequest = new RegisterRequest(userEmail, "differentPassword", "differentName");

        MvcResult mvcResult = performRegister(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath(".id").exists())
                .andExpect(jsonPath(".email").value(userEmail))
                .andExpect(jsonPath(".role").value("CUSTOMER"))
                .andReturn();
        var response = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), UserAccountResponse.class);
        accountIds.add(response.id());
        performRegister(secondRequest)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email is already registered."))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));

        assertAccountCustomerPersistence(response.id(), userEmail, userPassword, userName);
    }

    @Test
    public void shouldRejectRegisterWhenEmailIsNotWellFormed() throws Exception {
        String userEmail = "user_email_email.com";
        String userPassword = "user_password";
        String userName = "user_name";
        var request = new RegisterRequest(userEmail, userPassword, userName);

        performRegister(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Must be a well-formed email address."))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));

        assertAccountIsNotPersisted(userEmail);
    }

    @Test
    public void shouldRejectRegisterWhenEmailLengthIsGreaterThan255Chars() throws Exception {
        String userEmail = "a".repeat(64) + "@"
                + "b".repeat(63) + "."
                + "c".repeat(63) + "."
                + "d".repeat(59) + ".com";
        String userPassword = "user_password";
        String userName = "user_name";
        var request = new RegisterRequest(userEmail, userPassword, userName);

        performRegister(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email size must be between 0 and 255."))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));

        assertAccountIsNotPersisted(userEmail);
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void shouldRejectRegisterWhenEmailIsEmpty(String email) throws Exception {
        String userEmail = email;
        String userPassword = "user_password";
        String userName = "user_name";
        var request = new RegisterRequest(userEmail, userPassword, userName);

        performRegister(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("User email is required."))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));

        assertAccountIsNotPersisted(userEmail);
    }

    @ParameterizedTest
    @ValueSource(ints = { 11, 129 })
    public void shouldRejectRegisterWhenPasswordIsLessThan12CharsOrGreaterThan128(int passwordBounds) throws Exception {
        String userEmail = UUID.randomUUID() + "@email.com";
        String userPassword = "a".repeat(passwordBounds);
        String userName = "user_name";
        var request = new RegisterRequest(userEmail, userPassword, userName);

        performRegister(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password must contain between 12 and 128 characters."))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));

        assertAccountIsNotPersisted(userEmail);
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void shouldRejectRegisterWhenPasswordIsEmpty(String password) throws Exception {
        String userEmail = UUID.randomUUID() + "@email.com";
        String userPassword = password;
        String userName = "user_name";
        var request = new RegisterRequest(userEmail, userPassword, userName);

        performRegister(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("User password is required."))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));

        assertAccountIsNotPersisted(userEmail);
    }

    @Test
    public void shouldRejectRegisterWhenNameIsGreaterThan100Chars() throws Exception {
        String userEmail = UUID.randomUUID() + "@email.com";
        String userPassword = "user_password";
        String userName = randomString(101);
        var request = new RegisterRequest(userEmail, userPassword, userName);

        performRegister(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Name must not exceed 100 characters."))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));

        assertAccountIsNotPersisted(userEmail);
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void shouldRejectRegisterWhenNameIsEmpty(String name) throws Exception {
        String userEmail = UUID.randomUUID() + "@email.com";
        String userPassword = "user_password";
        String userName = name;
        var request = new RegisterRequest(userEmail, userPassword, userName);

        performRegister(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("User name is required."))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));

        assertAccountIsNotPersisted(userEmail);
    }

    private ResultActions performRegister(RegisterRequest request) throws Exception {
        return mvc.perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    private void assertAccountCustomerPersistence(UUID accountId, String userEmail, String userPassword,
            String userName) {
        transactionTemplate.executeWithoutResult(status -> {
            var persistedAccount = userAccountRepository.findById(accountId).orElseThrow();
            var persistedCustomer = customerRepository.findById(persistedAccount.getCustomerId()).orElseThrow();
            customerIds.add(persistedCustomer.getId());

            assertThat(persistedAccount.getRole()).isEqualTo(UserRole.CUSTOMER);
            assertThat(persistedAccount.getEmail()).isEqualTo(userEmail);
            assertThat(passwordEncoder.matches(userPassword, persistedAccount.getPasswordHash())).isTrue();
            assertThat(persistedCustomer.getName()).isEqualTo(userName);
            assertThat(persistedCustomer.getEmail()).isEqualTo(userEmail);
            assertThat(persistedCustomer.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
        });
    }

    private void assertAccountIsNotPersisted(String userEmail) {
        transactionTemplate.executeWithoutResult(status -> {
            Optional<UserAccountEntity> persisted = userAccountRepository.findByEmail(userEmail);
            if (persisted.isPresent()) {
                accountIds.add(persisted.get().getId());
                customerIds.add(persisted.get().getCustomerId());
            }

            assertThat(customerRepository.existsByEmail(userEmail)).isFalse();
            assertThat(persisted).isEmpty();
        });
    }

    private static String randomString(int len) {
        String pool = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        return RANDOM.ints(len, 0, pool.length())
                .mapToObj(i -> String.valueOf(pool.charAt(i)))
                .collect(Collectors.joining());
    }

}
