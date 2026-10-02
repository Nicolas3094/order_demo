package com.orders.messages.orders_demo.app.repositories;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.order.internal.OrderEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderItemEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderRepository;
import com.orders.messages.orders_demo.app.order.internal.OrderStatus;

@SpringBootTest
@Testcontainers
class OrderRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    public void shouldPersistAndFindOrder() {
        CustomerEntity customer = customerRepository.save(new CustomerEntity("test@example.com", "Test Customer"));
        OrderEntity order = OrderEntity.builder()
                .customerId(customer.getId())
                .currency(Currency.MXN)
                .addItem(
                        OrderItemEntity.builder()
                                .description("order item 1")
                                .unitPrice(new BigDecimal("1250.50"))
                                .build())
                .build();
        OrderEntity savedOrder = orderRepository.save(order);

        Optional<OrderEntity> result = orderRepository.findById(savedOrder.getId());

        assertThat(result).isPresent();
        OrderEntity persistedOrder = result.get();
        assertThat(persistedOrder.getId()).isEqualTo(savedOrder.getId());
        assertThat(persistedOrder.getCustomerId()).isEqualTo(customer.getId());
        assertThat(persistedOrder.getCurrency()).isEqualTo(Currency.MXN);
        assertThat(persistedOrder.getAmountTotal()).isEqualByComparingTo("1250.50");
        assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(persistedOrder.getCreatedAt()).isNotNull();
    }
}