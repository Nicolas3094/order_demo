package com.orders.messages.orders_demo.app.order.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.orders.messages.orders_demo.app.auth.internal.UserRole;
import com.orders.messages.orders_demo.app.auth.internal.security.AuthenticatedUser;

@ExtendWith(MockitoExtension.class)
class OrderAccessTest {
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    @Mock private OrderRepository orderRepository;
    private OrderAccess orderAccess;

    @BeforeEach
    void setUp() { orderAccess = new OrderAccess(orderRepository); }

    @Test
    void canAccessCustomer_WhenOwner_ShouldAllow() {
        assertThat(orderAccess.canAccessCustomer(customerAuthentication(), CUSTOMER_ID)).isTrue();
    }

    @Test
    void canAccessCustomer_WhenOtherCustomer_ShouldReject() {
        assertThat(orderAccess.canAccessCustomer(customerAuthentication(), UUID.randomUUID())).isFalse();
    }

    @Test
    void canAccessOrder_WhenOwner_ShouldAllow() {
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(OrderEntity.builder().customerId(CUSTOMER_ID).build()));

        assertThat(orderAccess.canAccessOrder(customerAuthentication(), ORDER_ID)).isTrue();
    }

    @Test
    void canAccessOrder_WhenOrderIsMissingOrBelongsToAnotherCustomer_ShouldReject() {
        assertThat(orderAccess.canAccessOrder(customerAuthentication(), ORDER_ID)).isFalse();
    }

    @Test
    void canAccessOrder_WhenAdmin_ShouldAllowWithoutOwnershipLookup() {
        var adminAuthentication = UsernamePasswordAuthenticationToken.authenticated("admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertThat(orderAccess.canAccessOrder(adminAuthentication, ORDER_ID)).isTrue();
        assertThat(orderAccess.canAccessCustomer(adminAuthentication, CUSTOMER_ID)).isTrue();
        verifyNoInteractions(orderRepository);
    }

    @Test
    void canAccessOrder_WhenAnonymousOrMissingAuthentication_ShouldRejectWithoutLookup() {
        var anonymousAuthentication = new AnonymousAuthenticationToken("key", "anonymous",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        assertThat(orderAccess.canAccessOrder(null, ORDER_ID)).isFalse();
        assertThat(orderAccess.canAccessOrder(anonymousAuthentication, ORDER_ID)).isFalse();
        assertThat(orderAccess.canAccessCustomer(null, CUSTOMER_ID)).isFalse();
        verifyNoInteractions(orderRepository);
    }

    @Test
    void canAccessOrder_WhenAuthenticationIsNotVerified_ShouldReject() {
        var authenticatedUser = new AuthenticatedUser(UUID.randomUUID(), CUSTOMER_ID, "customer@example.com", UserRole.CUSTOMER);
        var unverifiedAuthentication = UsernamePasswordAuthenticationToken.unauthenticated(authenticatedUser, null);

        assertThat(orderAccess.canAccessOrder(unverifiedAuthentication, ORDER_ID)).isFalse();
        assertThat(orderAccess.canAccessCustomer(unverifiedAuthentication, CUSTOMER_ID)).isFalse();
        verifyNoInteractions(orderRepository);
    }

    private Authentication customerAuthentication() {
        var authenticatedUser = new AuthenticatedUser(UUID.randomUUID(), CUSTOMER_ID, "customer@example.com", UserRole.CUSTOMER);
        return UsernamePasswordAuthenticationToken.authenticated(authenticatedUser, null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }
}
