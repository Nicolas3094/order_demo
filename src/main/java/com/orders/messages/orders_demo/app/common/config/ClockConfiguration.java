package com.orders.messages.orders_demo.app.common.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
