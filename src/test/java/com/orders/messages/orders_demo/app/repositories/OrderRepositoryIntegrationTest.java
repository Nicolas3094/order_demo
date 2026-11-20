package com.orders.messages.orders_demo.app.repositories;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.order.internal.OrderEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderItemEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderRepository;
import com.orders.messages.orders_demo.app.order.internal.OrderStatus;

import jakarta.transaction.Transactional;

@Transactional
class OrderRepositoryIntegrationTest extends PostgresContainer {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    public void shouldGetOrderByCustomerIdAndPageable() {
        CustomerEntity customer = customerRepository.save(createCustomer());
        Pageable pageable = PageRequest.of(0, 3, Sort.by("id").ascending());
        UUID orderId_1 = orderRepository.saveAndFlush(OrderEntity.builder()
                .customerId(customer.getId())
                .currency(Currency.MXN)
                .build()).getId();
        UUID orderId_2 = orderRepository.saveAndFlush(OrderEntity.builder()
                .customerId(customer.getId())
                .currency(Currency.MXN)
                .build()).getId();
        UUID orderId_3 = orderRepository.saveAndFlush(OrderEntity.builder()
                .customerId(customer.getId())
                .currency(Currency.MXN)
                .build()).getId();
        UUID orderId_4 = orderRepository.saveAndFlush(OrderEntity.builder()
                .customerId(customer.getId())
                .currency(Currency.MXN)
                .build()).getId();
        CustomerEntity otherCustomer = customerRepository.save(createCustomer());
        UUID otherCustomerOrderId = orderRepository.saveAndFlush(OrderEntity.builder()
                .customerId(otherCustomer.getId())
                .currency(Currency.MXN)
                .build()).getId();
        List<UUID> expectedOrderIds = List.of(orderId_1, orderId_2, orderId_3, orderId_4).stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        entityManager.clear();

        Page<OrderEntity> result = orderRepository.findByCustomerId(customer.getId(), pageable);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(OrderEntity::getId).containsExactlyElementsOf(expectedOrderIds.subList(0, 3));
        assertThat(result.getTotalElements()).isEqualTo(4);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result).extracting(OrderEntity::getId).doesNotContain(otherCustomerOrderId);

        Page<OrderEntity> nextPage = orderRepository.findByCustomerId(customer.getId(), pageable.next());

        assertThat(nextPage).extracting(OrderEntity::getId).containsExactly(expectedOrderIds.get(3));
        assertThat(nextPage.getTotalElements()).isEqualTo(4);
    }

    @Test
    public void shouldGetByIdAndCustomerId() {
        CustomerEntity customer = customerRepository.save(createCustomer());
        UUID orderId_1 = orderRepository.saveAndFlush(OrderEntity.builder()
                .customerId(customer.getId())
                .currency(Currency.MXN)
                .build()).getId();
        orderRepository.saveAndFlush(OrderEntity.builder()
                .customerId(customer.getId())
                .currency(Currency.MXN)
                .build()).getId();
        orderRepository.saveAndFlush(OrderEntity.builder()
                .customerId(customer.getId())
                .currency(Currency.MXN)
                .build()).getId();
        orderRepository.saveAndFlush(OrderEntity.builder()
                .customerId(customer.getId())
                .currency(Currency.MXN)
                .build()).getId();
        entityManager.clear();

        Optional<OrderEntity> result = orderRepository.findByIdAndCustomerId(orderId_1, customer.getId());

        assertThat(result).isNotEmpty();
        assertThat(result.get()).isEqualTo(OrderEntity.builder()
                .id(orderId_1)
                .customerId(customer.getId())
                .currency(Currency.MXN)
                .build());
    }

    @Test
    public void shouldGetOrderByStatusAndCustomerIdAndPageable() {
        CustomerEntity customer = customerRepository.save(createCustomer());
        Pageable pageable = PageRequest.of(0, 3, Sort.by("id").ascending());
        UUID orderId_1 = orderRepository.saveAndFlush(OrderEntity.builder()
                .customerId(customer.getId())
                .currency(Currency.MXN)
                .build()).getId();
        UUID orderId_2 = orderRepository.saveAndFlush(OrderEntity.builder()
                .customerId(customer.getId())
                .currency(Currency.MXN)
                .build()).getId();
        UUID orderId_3 = orderRepository.saveAndFlush(OrderEntity.builder()
                .customerId(customer.getId())
                .currency(Currency.MXN)
                .build()).getId();
        UUID orderId_4 = orderRepository.saveAndFlush(OrderEntity.builder()
                .customerId(customer.getId())
                .currency(Currency.MXN)
                .build()).getId();
        CustomerEntity otherCustomer = customerRepository.save(createCustomer());
        UUID otherCustomerOrderId = orderRepository.saveAndFlush(OrderEntity.builder()
                .customerId(otherCustomer.getId())
                .currency(Currency.MXN)
                .build()).getId();
        UUID cancelledOrderId = orderRepository.saveAndFlush(OrderEntity.builder()
                .customerId(customer.getId())
                .currency(Currency.MXN)
                .status(OrderStatus.CANCELLED)
                .build()).getId();
        List<UUID> expectedOrderIds = List.of(orderId_1, orderId_2, orderId_3, orderId_4).stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        entityManager.clear();

        Page<OrderEntity> result = orderRepository.findByCustomerIdAndStatus(
                customer.getId(), OrderStatus.PENDING_PAYMENT, pageable);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(OrderEntity::getId).containsExactlyElementsOf(expectedOrderIds.subList(0, 3));
        assertThat(result.getTotalElements()).isEqualTo(4);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result).extracting(OrderEntity::getId).doesNotContain(otherCustomerOrderId);
        assertThat(result).extracting(OrderEntity::getId).doesNotContain(cancelledOrderId);

        Page<OrderEntity> nextPage = orderRepository.findByCustomerIdAndStatus(
                customer.getId(), OrderStatus.PENDING_PAYMENT, pageable.next());

        assertThat(nextPage).extracting(OrderEntity::getId).containsExactly(expectedOrderIds.get(3));
        assertThat(nextPage.getTotalElements()).isEqualTo(4);
    }

    @Test
    public void shouldPersistAndFindOrder() {
        CustomerEntity customer = customerRepository.save(createCustomer());
        OrderEntity order = OrderEntity.builder()
                .customerId(customer.getId())
                .currency(Currency.MXN)
                .addItem(createDefaultOrderItem())
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
                .addItem(createDefaultOrderItem())
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
        assertThat(persistedOrder.getItems()).hasSize(2);
        assertThat(persistedOrder.getItems())
                .extracting(OrderItemEntity::getDescription)
                .containsExactlyInAnyOrder("order item 1", "order item 2");
        assertThat(persistedOrder.getItems()).allSatisfy(item -> {
            assertThat(item.getId()).isNotNull();
            assertThat(item.getOrder().getId()).isEqualTo(savedOrder.getId());
        });
        assertThat(persistedOrder.getItems())
                .extracting(OrderItemEntity::getLineTotal)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactlyInAnyOrder(new BigDecimal("1250.50"), new BigDecimal("2250.50"));
    }

    @Test
    public void shouldPersistOrderStatusChange() {
        CustomerEntity customer = customerRepository.save(createCustomer());
        OrderEntity order = OrderEntity.builder()
                .customerId(customer.getId())
                .currency(Currency.MXN)
                .addItem(createDefaultOrderItem())
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
                .addItem(createDefaultOrderItem())
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

    @Test
    void shouldDeleteRemovedItemThroughOrphanRemoval() {
        CustomerEntity customer = customerRepository.save(createCustomer());
        OrderEntity order = orderRepository.saveAndFlush(OrderEntity.builder()
                .customerId(customer.getId())
                .addItem(createDefaultOrderItem())
                .addItem(createOrderItem2())
                .build());
        UUID orderId = order.getId();
        entityManager.clear();
        OrderEntity managed = orderRepository.findById(orderId).orElseThrow();
        OrderItemEntity removed = managed.getItems().stream()
                .filter(item -> item.getDescription().equals("order item 1"))
                .findFirst().orElseThrow();
        UUID removedId = removed.getId();
        managed.removeItem(removed);
        orderRepository.flush();
        entityManager.clear();

        OrderEntity persisted = orderRepository.findById(orderId).orElseThrow();

        assertThat(persisted.getItems()).hasSize(1);
        assertThat(persisted.getAmountTotal()).isEqualByComparingTo("2250.50");
        assertThat(entityManager.find(OrderItemEntity.class, removedId)).isNull();
    }

    @Test
    void shouldCascadeDeleteOrderItems() {
        CustomerEntity customer = customerRepository.save(createCustomer());
        OrderEntity order = orderRepository.saveAndFlush(OrderEntity.builder()
                .customerId(customer.getId())
                .addItem(createDefaultOrderItem())
                .build());
        UUID orderId = order.getId();
        UUID itemId = order.getItems().get(0).getId();
        entityManager.clear();
        orderRepository.deleteById(orderId);
        orderRepository.flush();
        entityManager.clear();

        assertThat(orderRepository.findById(orderId)).isEmpty();
        assertThat(entityManager.find(OrderItemEntity.class, itemId)).isNull();
        assertThat(customerRepository.findById(customer.getId())).isPresent();
    }

    @Test
    void shouldReturnEmptyWhenOrderDoesNotExist() {
        assertThat(orderRepository.findById(UUID.randomUUID())).isEmpty();
    }

    private CustomerEntity createCustomer() {
        return new CustomerEntity(UUID.randomUUID() + "@example.com", "Test Customer");
    }

    private OrderItemEntity createDefaultOrderItem() {
        return OrderItemEntity.builder()
                .description("order item 1")
                .unitPrice(new BigDecimal("1250.50"))
                .build();
    }

    private OrderItemEntity createOrderItem(String descritpion, String unitPrince) {
        return OrderItemEntity.builder()
                .description(descritpion)
                .unitPrice(new BigDecimal(unitPrince))
                .build();
    }

    private OrderItemEntity createOrderItem2() {
        return OrderItemEntity.builder()
                .description("order item 2")
                .unitPrice(new BigDecimal("2250.50"))
                .build();
    }
}
