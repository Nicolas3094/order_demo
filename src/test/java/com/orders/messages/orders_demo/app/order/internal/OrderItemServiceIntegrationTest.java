package com.orders.messages.orders_demo.app.order.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;

public class OrderItemServiceIntegrationTest extends PostgresContainer {
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderItemService orderItemService;

    private final List<UUID> orderIds = new ArrayList<>();
    private final List<UUID> customerIds = new ArrayList<>();

    @AfterEach
    private void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            orderIds.forEach(orderRepository::deleteById);
            orderRepository.flush();
            customerIds.forEach(customerRepository::deleteById);
            customerRepository.flush();
        });
    }

    @Test
    public void shouldGetAllPersistedOrderItems() {
        UUID orderId = persistOrder();

        var result = transactionTemplate.execute(status -> orderItemService.getAllOrderItems(orderId));

        assertThat(result).hasSize(1);
        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.id()).isNotNull();
            assertThat(item.orderId()).isEqualTo(orderId);
            assertThat(item.sku()).isEqualTo("SKU");
            assertThat(item.description()).isEqualTo("order item 1");
            assertThat(item.unitPrice()).isEqualByComparingTo("123.50");
            assertThat(item.quantity()).isEqualTo(1L);
            assertThat(item.lineTotal()).isEqualByComparingTo("123.50");
        });
    }

    private UUID persistOrder() {
        return transactionTemplate.execute(status -> {
            UUID customerId = customerRepository
                    .saveAndFlush(new CustomerEntity(UUID.randomUUID() + "@example.com", "name")).getId();

            customerIds.add(customerId);

            var savedID = orderRepository.saveAndFlush(
                    OrderEntity.builder()
                            .customerId(customerId)
                            .addItem(OrderItemEntity.builder()
                                    .description("order item 1")
                                    .unitPrice(new BigDecimal("123.50"))
                                    .build())
                            .build())
                    .getId();

            orderIds.add(savedID);

            return savedID;
        });
    }
}
