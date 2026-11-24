package com.orders.messages.orders_demo.app.auth.internal;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.auth.api.AccountResponse;
import com.orders.messages.orders_demo.app.auth.api.AuthResponse;
import com.orders.messages.orders_demo.app.auth.api.LoginRequest;
import com.orders.messages.orders_demo.app.auth.api.RegisterRequest;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.order.internal.OrderEntity;
import com.orders.messages.orders_demo.app.order.api.OrderItemChangeQuantityRequest;
import com.orders.messages.orders_demo.app.order.internal.OrderItemEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderRepository;
import com.orders.messages.orders_demo.app.order.internal.OrderStatus;
import com.orders.messages.orders_demo.app.payment.internal.PaymentAttemptRepository;
import com.orders.messages.orders_demo.app.product.internal.ProductEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductRepository;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "payments.expiration.initial-delay=3600000",
        "payments.reconciliation.initial-delay=3600000"
})
class AuthIntegrationTest extends PostgresContainer {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final String PASSWORD = "a-long-test-password";
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;
    @MockitoSpyBean private UserAccountRepository accountRepository;
    @Autowired private AccessTokenRepository tokenRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private PaymentAttemptRepository paymentRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;
    @MockitoBean private Clock clock;
    private final List<UUID> accountIds = new ArrayList<>();
    private final List<UUID> customerIds = new ArrayList<>();
    private final List<UUID> orderIds = new ArrayList<>();
    private final List<UUID> productIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            orderIds.forEach(orderId -> paymentRepository.deleteAll(paymentRepository.findByOrderId(orderId)));
            paymentRepository.flush();
            orderIds.forEach(orderRepository::deleteById);
            orderRepository.flush();
            accountIds.forEach(tokenRepository::deleteByAccountId);
            tokenRepository.flush();
            accountIds.forEach(accountRepository::deleteById);
            accountRepository.flush();
            customerIds.forEach(customerRepository::deleteById);
            customerRepository.flush();
            productIds.forEach(productRepository::deleteById);
            productRepository.flush();
        });
    }

    @Test
    void shouldRegisterCustomerThroughHttpHashPasswordAndIgnoreRequestedAdminRole() throws Exception {
        String email = UUID.randomUUID() + "@EXAMPLE.COM";
        var registerPayload = Map.of("email", email, "password", PASSWORD, "name", " Customer ", "role", "ADMIN");

        var registerResult = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();
        var accountResponse = objectMapper.readValue(registerResult.getResponse().getContentAsString(), AccountResponse.class);
        trackAccount(accountResponse);
        var savedAccount = accountRepository.findById(accountResponse.id()).orElseThrow();
        assertThat(savedAccount.getEmail()).isEqualTo(email.toLowerCase());
        assertThat(savedAccount.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, savedAccount.getPasswordHash())).isTrue();
        assertThat(customerRepository.findById(accountResponse.customerId()).orElseThrow().getName()).isEqualTo("Customer");
    }

    @Test
    void shouldRejectDuplicateRegistrationIgnoringEmailCaseWithoutCreatingCustomer() throws Exception {
        var existingAccount = registerCustomer();
        long customerCount = customerRepository.count();
        long accountCount = accountRepository.count();

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(existingAccount.email().toUpperCase(), PASSWORD, "Other"))))
                .andExpect(status().isConflict());

        assertThat(customerRepository.count()).isEqualTo(customerCount);
        assertThat(accountRepository.count()).isEqualTo(accountCount);
    }

    @Test
    void shouldRejectRegistrationForExistingCustomerEmailWithoutTakingOverCustomer() throws Exception {
        var existingCustomer = customerRepository.saveAndFlush(new CustomerEntity(UUID.randomUUID() + "@example.com", "Existing"));
        customerIds.add(existingCustomer.getId());

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(existingCustomer.getEmail(), PASSWORD, "Other"))))
                .andExpect(status().isConflict());

        assertThat(accountRepository.findByEmail(existingCustomer.getEmail())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "short", "           " })
    void shouldRejectInvalidRegistrationPasswordWithoutPersistence(String invalidPassword) throws Exception {
        long customerCount = customerRepository.count();

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(UUID.randomUUID() + "@example.com", invalidPassword, "Customer"))))
                .andExpect(status().isBadRequest());

        assertThat(customerRepository.count()).isEqualTo(customerCount);
    }

    @Test
    void shouldRollBackBothCustomerAndAccountWhenRegistrationCallerFails() {
        String email = UUID.randomUUID() + "@example.com";

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            authService.register(new RegisterRequest(email, PASSWORD, "Customer"));
            entityManager.flush();
            throw new IllegalStateException("Caller failed.");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Caller failed.");

        assertThat(accountRepository.findByEmail(email)).isEmpty();
        assertThat(customerRepository.existsByEmailIgnoreCase(email)).isFalse();
    }

    @Test
    void shouldLoginThroughHttpAndUseBearerToGetCurrentAccountWithoutSessionCookie() throws Exception {
        var account = registerCustomer();
        var loginResult = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(account.email().toUpperCase(), PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        var authResponse = objectMapper.readValue(loginResult.getResponse().getContentAsString(), AuthResponse.class);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(account.id().toString()))
                .andExpect(jsonPath("$.customerId").value(account.customerId().toString()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(authResponse.expiresAt()).isEqualTo(NOW.plusSeconds(1800));
        assertThat(tokenRepository.findByTokenHash(AuthService.hashToken(authResponse.accessToken()))).isPresent();
        assertThat(tokenRepository.findByTokenHash(authResponse.accessToken())).isEmpty();
    }

    @Test
    void shouldRejectWrongPasswordAndMissingAccountWithSame401Message() throws Exception {
        var account = registerCustomer();

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(account.email(), "wrong-password"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Invalid email or password."));
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("missing@example.com", PASSWORD))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Invalid email or password."));

        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    void shouldRejectMissingMalformedUnknownAndExpiredBearerTokens() throws Exception {
        var account = registerCustomer();
        String accessToken = login(account);

        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer malformed"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"));
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + "z".repeat(43)))
                .andExpect(status().isUnauthorized());
        when(clock.instant()).thenReturn(NOW.plusSeconds(1800));
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRevokeOnlyPresentedTokenOnLogout() throws Exception {
        var account = registerCustomer();
        String firstAccessToken = login(account);
        String secondAccessToken = login(account);
        assertThat(firstAccessToken).isNotEqualTo(secondAccessToken);

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + firstAccessToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + firstAccessToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + secondAccessToken))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectLoginAndExistingTokenAfterCustomerIsBlocked() throws Exception {
        var account = registerCustomer();
        String accessToken = login(account);
        transactionTemplate.executeWithoutResult(status -> customerRepository.findById(account.customerId()).orElseThrow().deactivate());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(account.email(), PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowPublicProductReadsButRequireAuthenticationForOrdersAndCustomerData() throws Exception {
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/customers")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowOwnerToCreateReadAndListOrderAndRejectSpoofedCustomerId() throws Exception {
        var owner = registerCustomer();
        var otherCustomer = registerCustomer();
        String ownerToken = login(owner);
        long orderCount = orderRepository.count();
        String createOrderJson = objectMapper.writeValueAsString(Map.of("customerId", owner.customerId(), "currency", "MXN"));
        var createOrderResult = mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(createOrderJson))
                .andExpect(status().isCreated()).andReturn();
        UUID orderId = UUID.fromString(objectMapper.readTree(createOrderResult.getResponse().getContentAsString()).get("id").asText());
        orderIds.add(orderId);

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/customers/{customerId}/orders", owner.customerId())
                .header("Authorization", "Bearer " + ownerToken)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/customers/{customerId}", owner.customerId())
                .header("Authorization", "Bearer " + ownerToken)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("customerId", otherCustomer.customerId(), "currency", "MXN"))))
                .andExpect(status().isForbidden());

        assertThat(orderRepository.count()).isEqualTo(orderCount + 1);
    }

    @ParameterizedTest
    @ValueSource(strings = { "order", "items", "payments", "customer", "customer-orders", "customer-order" })
    void shouldRejectReadingAnotherCustomersResources(String resource) throws Exception {
        var owner = registerCustomer();
        var otherCustomer = registerCustomer();
        UUID orderId = persistOrder(owner.customerId());
        String otherCustomerToken = login(otherCustomer);
        var resourcePaths = Map.of(
                "order", "/api/v1/orders/" + orderId,
                "items", "/api/v1/orders/" + orderId + "/items",
                "payments", "/api/v1/orders/" + orderId + "/payments",
                "customer", "/api/v1/customers/" + owner.customerId(),
                "customer-orders", "/api/v1/customers/" + owner.customerId() + "/orders",
                "customer-order", "/api/v1/customers/" + owner.customerId() + "/orders/" + orderId);

        mockMvc.perform(get(resourcePaths.get(resource)).header("Authorization", "Bearer " + otherCustomerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectCancellingAnotherCustomersOrderWithoutChangingItsState() throws Exception {
        var owner = registerCustomer();
        var otherCustomer = registerCustomer();
        UUID orderId = persistOrder(owner.customerId());
        String otherCustomerToken = login(otherCustomer);

        mockMvc.perform(patch("/api/v1/orders/{orderId}/cancel", orderId)
                .header("Authorization", "Bearer " + otherCustomerToken)).andExpect(status().isForbidden());

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void shouldAllowOwnerToAddChangeAndDeleteItemsButRejectOtherCustomerAndPriceChanges() throws Exception {
        var owner = registerCustomer();
        var otherCustomer = registerCustomer();
        UUID orderId = persistOrder(owner.customerId());
        String ownerToken = login(owner);
        String otherCustomerToken = login(otherCustomer);
        var product = productRepository.saveAndFlush(ProductEntity.builder().sku(UUID.randomUUID().toString())
                .name("Product").price(new BigDecimal("100.00")).quantity(5L).build());
        productIds.add(product.getId());
        var createItemResult = mockMvc.perform(post("/api/v1/orders/{orderId}/items", orderId)
                .header("Authorization", "Bearer " + ownerToken).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("sku", product.getSku(), "quantity", 1))))
                .andExpect(status().isCreated()).andReturn();
        UUID itemId = UUID.fromString(objectMapper.readTree(createItemResult.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(patch("/api/v1/orders/{orderId}/items/{itemId}/quantity", orderId, itemId)
                .header("Authorization", "Bearer " + otherCustomerToken).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OrderItemChangeQuantityRequest(2L)))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/orders/{orderId}/items/{itemId}", orderId, itemId)
                .header("Authorization", "Bearer " + otherCustomerToken)).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/orders/{orderId}/items/{itemId}/price", orderId, itemId)
                .header("Authorization", "Bearer " + ownerToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"unitPrice\":1}")).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/orders/{orderId}/items/{itemId}/quantity", orderId, itemId)
                .header("Authorization", "Bearer " + ownerToken).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OrderItemChangeQuantityRequest(2L)))).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/orders/{orderId}/items/{itemId}", orderId, itemId)
                .header("Authorization", "Bearer " + ownerToken)).andExpect(status().isNoContent());

        assertThat(productRepository.findById(product.getId()).orElseThrow().getQuantity()).isEqualTo(5L);
    }

    @Test
    void shouldAllowOwnerCheckoutAndPaymentReadButRejectOtherCustomerCheckout() throws Exception {
        var owner = registerCustomer();
        var otherCustomer = registerCustomer();
        UUID orderId = persistOrderWithItem(owner.customerId());
        String ownerToken = login(owner);
        String otherCustomerToken = login(otherCustomer);
        String createPaymentJson = objectMapper.writeValueAsString(Map.of("provider", "STRIPE", "idempotencyKey", UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/orders/{orderId}/payments", orderId)
                .header("Authorization", "Bearer " + otherCustomerToken).contentType(MediaType.APPLICATION_JSON)
                .content(createPaymentJson)).andExpect(status().isForbidden());
        assertThat(paymentRepository.findByOrderId(orderId)).isEmpty();
        mockMvc.perform(post("/api/v1/orders/{orderId}/payments", orderId)
                .header("Authorization", "Bearer " + ownerToken).contentType(MediaType.APPLICATION_JSON)
                .content(createPaymentJson)).andExpect(status().isCreated());
        UUID paymentId = paymentRepository.findByOrderId(orderId).get(0).getId();
        mockMvc.perform(get("/api/v1/orders/{orderId}/payments/{paymentId}", orderId, paymentId)
                .header("Authorization", "Bearer " + ownerToken)).andExpect(status().isOk());
    }

    @Test
    void shouldRestrictAdministrativeOperationsAndAllowAdminToReadOtherOrders() throws Exception {
        var customer = registerCustomer();
        var administrator = registerAdministrator();
        UUID orderId = persistOrder(customer.customerId());
        String customerToken = login(customer);
        String adminToken = login(administrator);

        mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + customerToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + customerToken)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/products").header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/customers/{customerId}/block", administrator.customerId())
                .header("Authorization", "Bearer " + customerToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/customers/{customerId}/block", customer.customerId())
                .header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = { "processing", "succeeded", "failed", "cancel" })
    void shouldDisableManualPaymentTransitionsForBothCustomerAndAdminByDefault(String action) throws Exception {
        var customer = registerCustomer();
        var administrator = registerAdministrator();
        UUID orderId = persistOrder(customer.customerId());
        UUID paymentId = UUID.randomUUID();
        String customerToken = login(customer);
        String adminToken = login(administrator);

        mockMvc.perform(patch("/api/v1/orders/{orderId}/payments/{paymentId}/{action}", orderId, paymentId, action)
                .header("Authorization", "Bearer " + customerToken).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/orders/{orderId}/payments/{paymentId}/{action}", orderId, paymentId, action)
                .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldCreateOnlyOneAccountAndCustomerForConcurrentRegistrationWithSameEmail() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        long initialCustomerCount = customerRepository.count();
        long initialAccountCount = accountRepository.count();
        var registrationBarrier = new CyclicBarrier(2);
        var originalRepositoryAnswer = mockingDetails(accountRepository).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            boolean alreadyRegistered = (Boolean) originalRepositoryAnswer.answer(invocation);
            registrationBarrier.await(10, TimeUnit.SECONDS);
            return alreadyRegistered;
        }).when(accountRepository).existsByEmail(email);
        String registerPayload = objectMapper.writeValueAsString(new RegisterRequest(email, PASSWORD, "Customer"));
        var registrationExecutor = Executors.newFixedThreadPool(2);

        try {
            var firstRegistrationFuture = registrationExecutor.submit(() -> mockMvc.perform(
                    post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerPayload)).andReturn());
            var secondRegistrationFuture = registrationExecutor.submit(() -> mockMvc.perform(
                    post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerPayload)).andReturn());
            int firstStatus = firstRegistrationFuture.get(20, TimeUnit.SECONDS).getResponse().getStatus();
            int secondStatus = secondRegistrationFuture.get(20, TimeUnit.SECONDS).getResponse().getStatus();
            assertThat(List.of(firstStatus, secondStatus)).containsExactlyInAnyOrder(201, 409);
            var savedAccount = accountRepository.findByEmail(email).orElseThrow();
            trackAccount(new AccountResponse(savedAccount.getId(), savedAccount.getCustomerId(), savedAccount.getEmail(), savedAccount.getRole()));

            assertThat(customerRepository.count()).isEqualTo(initialCustomerCount + 1);
            assertThat(accountRepository.count()).isEqualTo(initialAccountCount + 1);
        } finally {
            registrationExecutor.shutdownNow();
            assertThat(registrationExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void shouldAllowOwnerCancellationButRejectCustomerExpireAndRefundOperations() throws Exception {
        var owner = registerCustomer();
        UUID orderId = persistOrder(owner.customerId());
        String ownerToken = login(owner);

        mockMvc.perform(patch("/api/v1/orders/{orderId}/expire", orderId)
                .header("Authorization", "Bearer " + ownerToken)).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/orders/{orderId}/refund", orderId)
                .header("Authorization", "Bearer " + ownerToken)).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/orders/{orderId}/cancel", orderId)
                .header("Authorization", "Bearer " + ownerToken)).andExpect(status().isOk());

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @ParameterizedTest
    @MethodSource("invalidRegisterRequests")
    void shouldRejectInvalidRegistrationFieldsBeforeCreatingCustomer(RegisterRequest invalidRegisterRequest) throws Exception {
        long initialCustomerCount = customerRepository.count();
        long initialAccountCount = accountRepository.count();

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRegisterRequest))).andExpect(status().isBadRequest());

        assertThat(customerRepository.count()).isEqualTo(initialCustomerCount);
        assertThat(accountRepository.count()).isEqualTo(initialAccountCount);
    }

    private static Stream<RegisterRequest> invalidRegisterRequests() {
        return Stream.of(
                new RegisterRequest("not-an-email", PASSWORD, "Customer"),
                new RegisterRequest(null, PASSWORD, "Customer"),
                new RegisterRequest("customer@example.com", PASSWORD, " "),
                new RegisterRequest("customer@example.com", PASSWORD, "n".repeat(101)),
                new RegisterRequest("customer@example.com", "p".repeat(129), "Customer"));
    }

    private AccountResponse registerCustomer() {
        var account = authService.register(new RegisterRequest(UUID.randomUUID() + "@example.com", PASSWORD, "Customer"));
        trackAccount(account);
        return account;
    }

    private AccountResponse registerAdministrator() {
        var account = registerCustomer();
        transactionTemplate.executeWithoutResult(status -> entityManager.createNativeQuery(
                "UPDATE user_account SET role = 'ADMIN' WHERE id = :accountId")
                .setParameter("accountId", account.id()).executeUpdate());
        return account;
    }

    private void trackAccount(AccountResponse account) {
        accountIds.add(account.id());
        customerIds.add(account.customerId());
    }

    private String login(AccountResponse account) {
        return authService.login(new LoginRequest(account.email(), PASSWORD)).accessToken();
    }

    private UUID persistOrder(UUID customerId) {
        var savedOrder = orderRepository.saveAndFlush(OrderEntity.builder().customerId(customerId).build());
        orderIds.add(savedOrder.getId());
        return savedOrder.getId();
    }

    private UUID persistOrderWithItem(UUID customerId) {
        return transactionTemplate.execute(status -> {
            var product = productRepository.saveAndFlush(ProductEntity.builder().sku(UUID.randomUUID().toString())
                    .name("Product").price(new BigDecimal("100.00")).quantity(5L).build());
            productIds.add(product.getId());
            var orderItem = OrderItemEntity.builder().sku(product.getSku()).unitPrice(product.getPrice()).quantity(1L).build();
            var savedOrder = orderRepository.saveAndFlush(OrderEntity.builder().customerId(customerId).addItem(orderItem).build());
            orderIds.add(savedOrder.getId());
            return savedOrder.getId();
        });
    }
}
