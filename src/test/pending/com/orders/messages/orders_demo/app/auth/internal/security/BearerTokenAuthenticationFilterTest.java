package com.orders.messages.orders_demo.app.auth.internal.security;

import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.messages.orders_demo.app.auth.internal.AuthService;
import com.orders.messages.orders_demo.app.auth.internal.UserRole;

@ExtendWith(MockitoExtension.class)
class BearerTokenAuthenticationFilterTest {
    @Mock private AuthService authService;
    private BearerTokenAuthenticationFilter authenticationFilter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        authenticationFilter = new BearerTokenAuthenticationFilter(authService,
                new SecurityErrorWriter(new ObjectMapper().findAndRegisterModules()));
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
    }

    @AfterEach
    void cleanUp() { SecurityContextHolder.clearContext(); }

    @Test
    void doFilter_WhenBearerIsValid_ShouldSetIdentityAndContinue() throws Exception {
        var authenticatedUser = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "customer@example.com", UserRole.CUSTOMER);
        when(authService.authenticate("valid-token")).thenReturn(authenticatedUser);
        request.addHeader("Authorization", "bearer valid-token");

        authenticationFilter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(authenticatedUser);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority").containsExactly("ROLE_CUSTOMER");
        assertThat(filterChain.getRequest()).isSameAs(request);
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void doFilter_WhenHeaderIsMissing_ShouldContinueWithoutAuthentication() throws Exception {
        request.setParameter("access_token", "query-token");

        authenticationFilter.doFilter(request, response, filterChain);

        assertThat(filterChain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(authService);
    }

    @Test
    void doFilter_WhenTokenIsInvalid_ShouldReturn401WithoutContinuing() throws Exception {
        request.addHeader("Authorization", "Bearer invalid-token");
        when(authService.authenticate("invalid-token")).thenThrow(new BadCredentialsException("Invalid token."));

        authenticationFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getContentAsString()).contains("Invalid or expired credentials.");
        assertThat(filterChain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_WhenSchemeIsNotBearer_ShouldRejectWithoutCredentialLookup() throws Exception {
        request.addHeader("Authorization", "Basic credentials");

        authenticationFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(filterChain.getRequest()).isNull();
        verifyNoInteractions(authService);
    }
}
