package com.orders.messages.orders_demo.app.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class AdminHttpTestConfiguration {
    @Bean
    public MockMvcBuilderCustomizer administratorRequests() {
        return builder -> builder.defaultRequest(get("/").with(user("test-admin").roles("ADMIN")));
    }
}
