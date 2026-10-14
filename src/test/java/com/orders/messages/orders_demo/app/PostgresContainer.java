package com.orders.messages.orders_demo.app;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import jakarta.persistence.EntityManager;

@SpringBootTest
@Testcontainers
// The container restarts per test class; rebuild the datasource with its new port.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class PostgresContainer {
    @Container
    @ServiceConnection
    protected static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    protected EntityManager entityManager;
}
