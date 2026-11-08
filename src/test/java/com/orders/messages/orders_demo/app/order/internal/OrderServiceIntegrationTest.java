package com.orders.messages.orders_demo.app.order.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.order.api.CreateOrderRequest;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderNotFoundException;

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
        UUID orderId1 = persistOrder();
        UUID orderId2 = persistOrder();

        var result = orderService.getAllOrders();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(order -> order.id()).containsExactlyInAnyOrder(orderId1, orderId2);
    }

    @Test
    public void shouldReturnEmptyListWhenNoOrdersExist() {
        var result = orderService.getAllOrders();

        assertThat(result).isEmpty();
    }

    @Test
    public void shouldGetPersistedOrderById() {
        UUID orderId = persistOrder();

        var result = orderService.getOrder(orderId);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(orderId);
    }

    @Test
    public void shouldRejectGettingMissingOrder() {
        assertThatThrownBy(() -> orderService.getOrder(UUID.randomUUID()))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    public void shouldCreateAndPersistOrderForExistingCustomer() {
        UUID customerId = persistCustomer();
        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(customerId)
                .currency(Currency.MXN)
                .build();
        UUID orderId = orderService.createOrder(request).id();

        transactionTemplate.executeWithoutResult(status -> {
            orderIds.add(orderId);

            OrderEntity persisted = orderRepository.findById(orderId).orElseThrow();

            assertThat(persisted).isNotNull();
            assertThat(persisted.getId()).isNotNull();
            assertThat(persisted.getCustomerId()).isEqualTo(customerId);
            assertThat(persisted.getCurrency()).isEqualTo(Currency.MXN);
            assertThat(persisted.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(persisted.getItems()).hasSize(0);
        });

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

    private UUID persistCustomer() {
        return transactionTemplate.execute(status -> {
            UUID savedCustomerID = customerRepository
                    .saveAndFlush(new CustomerEntity(UUID.randomUUID() + "@example.com", "name"))
                    .getId();

            customerIds.add(savedCustomerID);

            return savedCustomerID;
        });
    }
}
