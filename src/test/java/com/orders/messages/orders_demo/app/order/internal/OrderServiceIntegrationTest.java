package com.orders.messages.orders_demo.app.order.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;

public class OrderServiceIntegrationTest extends PostgresContainer {
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderService orderService;

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
    public void shouldGetAllPersistedOrders() {
    }

    @Test
    public void shouldReturnEmptyListWhenNoOrdersExist() {
    }

    @Test
    public void shouldGetPersistedOrderById() {
    }

    @Test
    public void shouldRejectGettingMissingOrder() {
    }

    @Test
    public void shouldCreateAndPersistOrderForExistingCustomer() {
    }

    @Test
    public void shouldRejectCreatingOrderForMissingCustomer() {
    }

    @Test
    public void shouldCancelOrderAndRestoreProductStock() {
    }

    @Test
    public void shouldExpireOrderAndRestoreProductStock() {
    }

    @Test
    public void shouldRefundOrderAndRestoreProductStock() {
    }

    @Test
    public void shouldRejectCancellingOrderInInvalidState() {
    }

    @Test
    public void shouldRejectExpiringOrderInInvalidState() {
    }

    @Test
    public void shouldRejectRefundingOrderInInvalidState() {
    }

    @Test
    public void shouldRejectStateChangesForMissingOrder() {
    }

    @Test
    public void shouldRollBackCancellationWhenStockRestorationFails() {
    }

    @Test
    public void shouldRollBackExpirationWhenStockRestorationFails() {
    }

    @Test
    public void shouldRollBackRefundWhenStockRestorationFails() {
    }

    private UUID persistOrder() {
        UUID customerId = customerRepository
                .saveAndFlush(new CustomerEntity(UUID.randomUUID() + "@example.com", "name")).getId();
        UUID orderId = transactionTemplate.execute(status -> {
            return orderRepository.saveAndFlush(
                    OrderEntity.builder()
                            .customerId(customerId)
                            .addItem(OrderItemEntity.builder()
                                    .description("order item 1")
                                    .unitPrice(new BigDecimal("123.50"))
                                    .build())
                            .build())
                    .getId();
        });

        orderIds.add(orderId);
        customerIds.add(customerId);

        return orderId;
    }
}
