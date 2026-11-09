package com.orders.messages.orders_demo.app.repositories;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.order.internal.OrderEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderItemEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderItemRepository;
import com.orders.messages.orders_demo.app.order.internal.OrderRepository;

@Transactional
class OrderItemRepositoryIntegrationTest extends PostgresContainer {
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private OrderRepository orderRepository;

    @Test
    void shouldPersistAndReloadItemWithAllMappedFields() {
        CustomerEntity customer = customerRepository.save(createCustomer());
        OrderEntity order = orderRepository.saveAndFlush(OrderEntity.builder()
                .customerId(customer.getId())
                .currency(Currency.MXN)
                .build());
        OrderItemEntity orderItem = createOrderItem("SKU-001");
        orderItem.attachOrder(order);
        UUID orderItemId = orderItemRepository.saveAndFlush(orderItem).getId();
        entityManager.clear();

        Optional<OrderItemEntity> result = orderItemRepository.findById(orderItemId);

        assertThat(result).isPresent();
        OrderItemEntity persistedItem = result.get();
        assertThat(persistedItem.getId()).isEqualTo(orderItemId);
        assertThat(persistedItem.getSku()).isEqualTo("SKU-001");
        assertThat(persistedItem.getDescription()).isEqualTo("Description of the item");
        assertThat(persistedItem.getUnitPrice()).isEqualByComparingTo("1250.50");
        assertThat(persistedItem.getQuantity()).isEqualTo(2L);
        assertThat(persistedItem.getLineTotal()).isEqualByComparingTo("2501.00");
        assertThat(persistedItem.getOrder().getId()).isEqualTo(order.getId());
    }

    @Test
    void shouldLoadAssociatedOrderWithinTransaction() {
        CustomerEntity customer = customerRepository.save(createCustomer());
        OrderEntity order = orderRepository.saveAndFlush(OrderEntity.builder()
                .customerId(customer.getId())
                .currency(Currency.MXN)
                .build());
        OrderItemEntity orderItem = createOrderItem("SKU-001");
        orderItem.attachOrder(order);
        UUID orderItemId = orderItemRepository.saveAndFlush(orderItem).getId();
        entityManager.clear();

        OrderItemEntity result = orderItemRepository.findById(orderItemId).orElseThrow();

        OrderEntity associatedOrder = result.getOrder();
        assertThat(associatedOrder.getId()).isEqualTo(order.getId());
        assertThat(associatedOrder.getCurrency()).isEqualTo(Currency.MXN);
        assertThat(associatedOrder.getCustomerId()).isEqualTo(customer.getId());
    }

    @Test
    void shouldPersistUnitPriceChangeAndUpdatedLineTotal() {
        CustomerEntity customer = customerRepository.save(createCustomer());
        OrderEntity order = orderRepository.saveAndFlush(OrderEntity.builder()
                .customerId(customer.getId())
                .currency(Currency.MXN)
                .build());
        OrderItemEntity orderItem = createOrderItem("SKU-001");
        orderItem.attachOrder(order);
        UUID orderItemId = orderItemRepository.saveAndFlush(orderItem).getId();
        entityManager.clear();
        orderItem = orderItemRepository.findById(orderItemId).orElseThrow();
        orderItem.changeUnitPrice(new BigDecimal("632.00"));
        orderItemRepository.saveAndFlush(orderItem);
        entityManager.clear();

        OrderItemEntity result = orderItemRepository.findById(orderItemId).orElseThrow();

        assertThat(result.getUnitPrice()).isEqualByComparingTo("632.00");
        assertThat(result.getLineTotal()).isEqualByComparingTo("1264.00");
    }

    @Test
    void shouldPersistQuantityChangeAndUpdatedLineTotal() {
        CustomerEntity customer = customerRepository.save(createCustomer());
        OrderEntity order = orderRepository.saveAndFlush(OrderEntity.builder()
                .customerId(customer.getId())
                .currency(Currency.MXN)
                .build());
        OrderItemEntity orderItem = createOrderItem("SKU-001");
        orderItem.attachOrder(order);
        UUID orderItemId = orderItemRepository.saveAndFlush(orderItem).getId();
        entityManager.clear();
        orderItem = orderItemRepository.findById(orderItemId).orElseThrow();
        orderItem.changeQuantity(4L);
        orderItemRepository.saveAndFlush(orderItem);
        entityManager.clear();

        OrderItemEntity result = orderItemRepository.findById(orderItemId).orElseThrow();

        assertThat(result.getQuantity()).isEqualTo(4L);
        assertThat(result.getLineTotal()).isEqualByComparingTo("5002.00");
    }

    @Test
    void shouldRejectPersistingItemWithoutOrder() {
        OrderItemEntity orderItem = createOrderItem("SKU-001");
        entityManager.clear();

        assertThatThrownBy(() -> orderItemRepository.saveAndFlush(orderItem))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectPersistingItemReferencingMissingOrder() {
        UUID missingOrderId = UUID.randomUUID();
        OrderEntity orderReference = entityManager.getReference(OrderEntity.class, missingOrderId);
        OrderItemEntity orderItem = createOrderItem("SKU-001");
        orderItem.attachOrder(orderReference);
        entityManager.clear();

        assertThatThrownBy(() -> orderItemRepository.saveAndFlush(orderItem))
                .isInstanceOf(DataIntegrityViolationException.class);

    }

    @Test
    void shouldRejectDescriptionExceedingColumnLength() {
    }

    private CustomerEntity createCustomer() {
        return new CustomerEntity(UUID.randomUUID() + "@example.com", "Test Customer");
    }

    private OrderItemEntity createOrderItem(String sku) {
        return OrderItemEntity.builder()
                .sku(sku)
                .description("Description of the item")
                .quantity(2L)
                .unitPrice(new BigDecimal("1250.50"))
                .build();
    }

}
