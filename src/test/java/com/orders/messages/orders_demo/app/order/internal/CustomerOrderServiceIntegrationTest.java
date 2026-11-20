package com.orders.messages.orders_demo.app.order.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.support.TransactionTemplate;

import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerNotFoundException;
import com.orders.messages.orders_demo.app.order.api.OrderResponse;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderNotFoundException;

public class CustomerOrderServiceIntegrationTest extends PostgresContainer {

    @Autowired
    private CustomerOrderService customerOrderService;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<UUID> customerIds = new ArrayList<>();
    private final List<UUID> orderIds = new ArrayList<>();

    private static final Pageable DEFAULT_PAGEABLE = PageRequest.of(0, 15, Sort.by("id").ascending());

    @AfterEach
    public void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            orderIds.forEach(orderRepository::deleteById);
            orderRepository.flush();
            customerIds.forEach(customerRepository::deleteById);
            customerRepository.flush();
        });
        orderIds.clear();
        customerIds.clear();
    }

    @Test
    public void shouldListOnlyRequestedCustomerOrdersAcrossStatusesWhenStatusIsNull() {
        UUID customerId = persistCustomer();
        UUID otherCustomerId = persistCustomer();
        UUID pendingOrderId = persistOrder(customerId, OrderStatus.PENDING_PAYMENT);
        UUID paidOrderId = persistOrder(customerId, OrderStatus.PAID);
        persistOrder(otherCustomerId, OrderStatus.PENDING_PAYMENT);

        var result = customerOrderService.getCustomerOrders(customerId, null, DEFAULT_PAGEABLE);

        assertThat(result).extracting(OrderResponse::id).containsExactlyInAnyOrder(pendingOrderId, paidOrderId);
        assertThat(result).extracting(OrderResponse::customerId).containsOnly(customerId);
        assertThat(result).extracting(OrderResponse::status).containsExactlyInAnyOrder(OrderStatus.PENDING_PAYMENT,
                OrderStatus.PAID);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    public void shouldListOnlyOrdersMatchingCustomerAndStatus(OrderStatus orderStatus) {
        UUID customerId = persistCustomer();
        UUID otherCustomerId = persistCustomer();
        UUID matchingOrderId = persistOrder(customerId, orderStatus);
        OrderStatus otherStatus = orderStatus == OrderStatus.PAID ? OrderStatus.PENDING_PAYMENT : OrderStatus.PAID;
        persistOrder(customerId, otherStatus);
        persistOrder(otherCustomerId, orderStatus);

        var result = customerOrderService.getCustomerOrders(customerId, orderStatus, DEFAULT_PAGEABLE);

        assertThat(result).extracting(OrderResponse::id).containsExactly(matchingOrderId);
        assertThat(result).extracting(OrderResponse::customerId).containsOnly(customerId);
        assertThat(result).extracting(OrderResponse::status).containsOnly(orderStatus);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PENDING_PAYMENT")
    @NullSource
    public void shouldReturnRequestedPageWithCorrectTotalsAndSorting(OrderStatus status) {
        UUID customerId = persistCustomer();
        List<UUID> expectedIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            expectedIds.add(persistOrder(customerId, OrderStatus.PENDING_PAYMENT));
        }
        expectedIds.sort(Comparator.comparing(UUID::toString).reversed());
        Pageable pageable = PageRequest.of(1, 2, Sort.by("id").descending());

        var result = customerOrderService.getCustomerOrders(customerId, status, pageable);

        assertThat(result).extracting(OrderResponse::id).containsExactlyElementsOf(expectedIds.subList(2, 4));
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getSort()).isEqualTo(pageable.getSort());
    }

    @Test
    public void shouldReturnEmptyPageWhenCustomerHasNoOrders() {
        UUID customerId = persistCustomer();
        persistOrder(persistCustomer(), OrderStatus.PENDING_PAYMENT);

        var result = customerOrderService.getCustomerOrders(customerId, null, DEFAULT_PAGEABLE);

        assertThat(result).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getTotalPages()).isZero();
    }

    @Test
    public void shouldReturnEmptyPageWhenNoOrdersMatchStatus() {
        UUID customerId = persistCustomer();
        persistOrder(customerId, OrderStatus.PENDING_PAYMENT);
        persistOrder(persistCustomer(), OrderStatus.PAID);

        var result = customerOrderService.getCustomerOrders(customerId, OrderStatus.PAID, DEFAULT_PAGEABLE);

        assertThat(result).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    public void shouldReturnEmptyContentWithCorrectTotalsWhenPageIsOutOfRange() {
        UUID customerId = persistCustomer();
        persistOrder(customerId, OrderStatus.PENDING_PAYMENT);
        Pageable pageable = PageRequest.of(2, 2, Sort.by("id").ascending());

        var result = customerOrderService.getCustomerOrders(customerId, null, pageable);

        assertThat(result).isEmpty();
        assertThat(result.getNumber()).isEqualTo(2);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getTotalPages()).isEqualTo(1);
    }

    @Test
    public void shouldGetPersistedOrderBelongingToCustomerWithMappedFields() {
        UUID customerId = persistCustomer();
        UUID orderId = persistOrder(customerId, OrderStatus.PAID);
        OrderEntity persistedOrder = transactionTemplate
                .execute(status -> orderRepository.findById(orderId).orElseThrow());

        var result = customerOrderService.getOrder(orderId, customerId);

        assertThat(result.id()).isEqualTo(orderId);
        assertThat(result.customerId()).isEqualTo(customerId);
        assertThat(result.currency()).isEqualTo(Currency.MXN);
        assertThat(result.status()).isEqualTo(OrderStatus.PAID);
        assertThat(result.amountTotal()).isEqualByComparingTo("247.00");
        assertThat(result.createdAt()).isNotNull().isEqualTo(persistedOrder.getCreatedAt());
        assertThat(result.updatedAt()).isNotNull().isEqualTo(persistedOrder.getUpdatedAt());
        assertThat(result.expiresAt()).isEqualTo(persistedOrder.getExpiresAt());
    }

    @Test
    public void shouldRejectGettingOrderBelongingToAnotherCustomer() {
        UUID customerId = persistCustomer();
        UUID orderId = persistOrder(persistCustomer(), OrderStatus.PENDING_PAYMENT);

        assertThatThrownBy(() -> customerOrderService.getOrder(orderId, customerId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order could not be found.");
    }

    @Test
    public void shouldRejectGettingMissingOrderForExistingCustomer() {
        UUID customerId = persistCustomer();

        assertThatThrownBy(() -> customerOrderService.getOrder(UUID.randomUUID(), customerId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order could not be found.");
    }

    @Test
    public void shouldRejectGettingOrderForMissingCustomer() {
        UUID orderId = persistOrder(persistCustomer(), OrderStatus.PENDING_PAYMENT);

        assertThatThrownBy(() -> customerOrderService.getOrder(orderId, UUID.randomUUID()))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessage("Customer could not be found.");
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    @NullSource
    public void shouldRejectListingOrdersForMissingCustomer(OrderStatus status) {
        assertThatThrownBy(() -> customerOrderService.getCustomerOrders(UUID.randomUUID(), status, DEFAULT_PAGEABLE))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessage("Customer could not be found.");
    }

    private UUID persistCustomer() {
        return transactionTemplate.execute(status -> {
            UUID customerId = customerRepository.saveAndFlush(
                    new CustomerEntity(UUID.randomUUID() + "@example.com", "Test Customer")).getId();
            customerIds.add(customerId);
            return customerId;
        });
    }

    private UUID persistOrder(UUID customerId, OrderStatus orderStatus) {
        return transactionTemplate.execute(status -> {
            UUID orderId = orderRepository.saveAndFlush(OrderEntity.builder()
                    .customerId(customerId)
                    .currency(Currency.MXN)
                    .status(orderStatus)
                    .addItem(OrderItemEntity.builder()
                            .sku("SKU-123")
                            .description("Test item")
                            .unitPrice(new BigDecimal("123.50"))
                            .quantity(2L)
                            .build())
                    .build()).getId();
            orderIds.add(orderId);
            return orderId;
        });
    }
}
