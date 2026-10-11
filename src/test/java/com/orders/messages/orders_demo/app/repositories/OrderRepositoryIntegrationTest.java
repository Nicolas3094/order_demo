package com.orders.messages.orders_demo.app.repositories;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.order.internal.OrderEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderItemEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderRepository;
import com.orders.messages.orders_demo.app.order.internal.OrderStatus;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@SpringBootTest
@Testcontainers
@Transactional
class OrderRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    public void shouldPersistAndFindOrder() {
        CustomerEntity customer = customerRepository.save(createCustomer());
        OrderEntity order = OrderEntity.builder()
                .customerId(customer.getId())
                .currency(Currency.MXN)
                .addItem(createOrderItem())
                .build();
        OrderEntity savedOrder = orderRepository.saveAndFlush(order);
        var orderId = savedOrder.getId();
        entityManager.clear();

        Optional<OrderEntity> result = orderRepository.findById(orderId);

        assertThat(result).isPresent();
        OrderEntity persistedOrder = result.get();
        assertThat(persistedOrder.getId()).isEqualTo(savedOrder.getId());
        assertThat(persistedOrder.getCustomerId()).isEqualTo(customer.getId());
        assertThat(persistedOrder.getCurrency()).isEqualTo(Currency.MXN);
        assertThat(persistedOrder.getAmountTotal()).isEqualByComparingTo("1250.50");
        assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(persistedOrder.getCreatedAt()).isNotNull();
    }

    @Test
    public void shouldPersistOrderItems() {
        CustomerEntity customer = customerRepository.save(createCustomer());
        OrderEntity order = OrderEntity.builder()
                .customerId(customer.getId())
                .currency(Currency.MXN)
                .addItem(createOrderItem())
                .addItem(createOrderItem2())
                .build();
        OrderEntity savedOrder = orderRepository.saveAndFlush(order);
        entityManager.clear();

        Optional<OrderEntity> result = orderRepository.findById(savedOrder.getId());

        assertThat(result).isPresent();
        OrderEntity persistedOrder = result.get();
        assertThat(persistedOrder.getId()).isEqualTo(savedOrder.getId());
        assertThat(persistedOrder.getCustomerId()).isEqualTo(customer.getId());
        assertThat(persistedOrder.getCurrency()).isEqualTo(Currency.MXN);
        assertThat(persistedOrder.getAmountTotal()).isEqualByComparingTo("3501.00");
        assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(persistedOrder.getCreatedAt()).isNotNull();
        assertThat(persistedOrder.getItems().size()).isEqualTo(2);
    }

    @Test
    public void shouldPersistOrderStatusChange() {
        CustomerEntity customer = customerRepository.save(createCustomer());
        OrderEntity order = OrderEntity.builder()
                .customerId(customer.getId())
                .currency(Currency.MXN)
                .addItem(createOrderItem())
                .build();
        OrderEntity savedOrder = orderRepository.saveAndFlush(order);
        savedOrder.cancelOrder();
        OrderEntity secondSavedOrder = orderRepository.saveAndFlush(savedOrder);
        UUID orderId = secondSavedOrder.getId();
        entityManager.clear();

        Optional<OrderEntity> result = orderRepository.findById(orderId);

        assertThat(result).isPresent();
        OrderEntity persistedOrder = result.get();
        assertThat(persistedOrder.getId()).isEqualTo(orderId);
        assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    public void shouldIncrementVersionWhenOrderIsUpdated() {
        CustomerEntity customer = customerRepository.save(createCustomer());
        OrderEntity order = OrderEntity.builder()
                .customerId(customer.getId())
                .currency(Currency.MXN)
                .addItem(createOrderItem())
                .build();
        OrderEntity savedOrder = orderRepository.saveAndFlush(order);
        Long version1 = savedOrder.getVersion();
        savedOrder.cancelOrder();
        OrderEntity secondSavedOrder = orderRepository.saveAndFlush(savedOrder);
        Long version2 = secondSavedOrder.getVersion();
        UUID orderId = secondSavedOrder.getId();
        entityManager.clear();

        Optional<OrderEntity> result = orderRepository.findById(orderId);

        assertThat(result).isPresent();
        OrderEntity persistedOrder = result.get();
        assertThat(persistedOrder.getId()).isEqualTo(orderId);
        assertThat(version2).isEqualTo(version1 + 1);
        assertThat(version2).isEqualTo(persistedOrder.getVersion());
    }

    private CustomerEntity createCustomer() {
        return new CustomerEntity("test@example.com", "Test Customer");
    }

    private OrderItemEntity createOrderItem() {
        return OrderItemEntity.builder()
                .description("order item 1")
                .unitPrice(new BigDecimal("1250.50"))
                .build();
    }

    private OrderItemEntity createOrderItem2() {
        return OrderItemEntity.builder()
                .description("order item 2")
                .unitPrice(new BigDecimal("2250.50"))
                .build();
    }
}