package com.orders.messages.orders_demo.app.order.internal;

import static org.assertj.core.api.Assertions.*;

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
import com.orders.messages.orders_demo.app.order.api.CreateOrderItemRequest;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderItemNotFoundException;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderNotFoundException;
import com.orders.messages.orders_demo.app.product.internal.ProductEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductRepository;

public class OrderItemServiceIntegrationTest extends PostgresContainer {
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderItemService orderItemService;

    private final List<UUID> orderIds = new ArrayList<>();
    private final List<UUID> customerIds = new ArrayList<>();
    private final List<UUID> productIds = new ArrayList<>();

    private static final Long INITIAL_PRODUCT_STOCK = 10L;
    private static final String PRODUCT_SKU = "SKU-123";

    @AfterEach
    private void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            orderIds.forEach(orderRepository::deleteById);
            orderRepository.flush();
            customerIds.forEach(customerRepository::deleteById);
            customerRepository.flush();
            productIds.forEach(productRepository::deleteById);
            productRepository.flush();
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

    @Test
    public void shouldReturnEmptyListWhenOrderHasNoItems() {
        UUID orderId = persistOrderWithEmptyItems();

        var result = transactionTemplate.execute(status -> orderItemService.getAllOrderItems(orderId));

        assertThat(result).isEmpty();
    }

    @Test
    public void shouldRejectListingItemsForMissingOrder() {
        UUID missingOrderId = UUID.randomUUID();

        assertThatThrownBy(() -> orderItemService.getAllOrderItems(missingOrderId))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    public void shouldGetPersistedOrderItemById() {
        UUID orderId = persistOrder();
        UUID orderItemId = getFirstOrderItem(orderId);

        var result = transactionTemplate.execute(status -> orderItemService.getOrderItem(orderId, orderItemId));

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(orderItemId);
        assertThat(result.orderId()).isEqualTo(orderId);
    }

    @Test
    public void shouldRejectGettingMissingOrderItem() {
        UUID orderId = persistOrder();
        UUID missingOrderItemId = UUID.randomUUID();

        assertThatThrownBy(() -> orderItemService.getOrderItem(orderId, missingOrderItemId))
                .isInstanceOf(OrderItemNotFoundException.class);
    }

    @Test
    public void shouldRejectAccessingItemFromAnotherOrder() {
        UUID orderId1 = persistOrder();
        UUID orderId2 = persistOrder();
        UUID orderItemIdFromOrder1 = getFirstOrderItem(orderId1);

        assertThatThrownBy(() -> orderItemService.getOrderItem(orderId2, orderItemIdFromOrder1))
                .isInstanceOf(OrderItemNotFoundException.class);
    }

    @Test
    public void shouldCreateItemDecreaseStockAndUpdateOrderTotal() {
        UUID orderId = persistOrder();
        UUID productId = persistProduct();
        CreateOrderItemRequest request = new CreateOrderItemRequest(PRODUCT_SKU, 2L);

        transactionTemplate.executeWithoutResult(status -> orderItemService.createOrderItem(orderId, request));

        transactionTemplate.executeWithoutResult(status -> {
            OrderEntity order = orderRepository.findById(orderId).orElseThrow();
            ProductEntity product = productRepository.findById(productId).orElseThrow();

            assertThat(order.getItems()).hasSize(2);
            assertThat(order.getAmountTotal()).isEqualByComparingTo("370.50");
            assertThat(product.getQuantity()).isEqualTo(INITIAL_PRODUCT_STOCK - 2);
        });
    }

    @Test
    public void shouldRejectCreatingItemForMissingOrder() {
        UUID missingOrderId = UUID.randomUUID();

    }

    @Test
    public void shouldRejectCreatingItemForMissingProduct() {
    }

    @Test
    public void shouldRejectCreatingItemForInactiveProduct() {
    }

    @Test
    public void shouldRejectCreatingItemWithDifferentCurrency() {
    }

    @Test
    public void shouldRejectCreatingItemWithInsufficientStockWithoutChangingPersistedState() {
    }

    @Test
    public void shouldRejectItemMutationsWhenOrderIsNotPending() {
    }

    @Test
    public void shouldChangeUnitPriceAndUpdateLineAndOrderTotalsWithoutChangingStock() {
    }

    @Test
    public void shouldIncreaseQuantityDecreaseStockAndUpdateTotals() {
    }

    @Test
    public void shouldDecreaseQuantityRestoreStockAndUpdateTotals() {
    }

    @Test
    public void shouldPreserveStockAndTotalsWhenQuantityIsUnchanged() {
    }

    @Test
    public void shouldRejectIncreasingQuantityWithInsufficientStockWithoutChangingPersistedState() {
    }

    @Test
    public void shouldDeleteItemRestoreStockAndUpdateOrderTotal() {
    }

    @Test
    public void shouldRejectMutatingMissingOrderItem() {
    }

    @Test
    public void shouldRejectMutatingItemFromAnotherOrder() {
    }

    @Test
    public void shouldRollBackItemCreationAndStockDeductionWhenCallerTransactionFails() {
    }

    @Test
    public void shouldRollBackQuantityChangeAndStockAdjustmentWhenCallerTransactionFails() {
    }

    @Test
    public void shouldRollBackItemDeletionWhenStockRestorationFails() {
    }

    private UUID getFirstOrderItem(UUID orderId) {
        return transactionTemplate.execute(status -> {
            OrderEntity order = orderRepository.findById(orderId).orElseThrow();
            var item = order.getItems().stream().findFirst().orElseThrow();
            return item.getId();
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

    private UUID persistOrderWithEmptyItems() {
        return transactionTemplate.execute(status -> {
            UUID customerId = customerRepository
                    .saveAndFlush(new CustomerEntity(UUID.randomUUID() + "@example.com", "name")).getId();

            customerIds.add(customerId);

            var savedID = orderRepository.saveAndFlush(OrderEntity.builder().customerId(customerId).build()).getId();

            orderIds.add(savedID);

            return savedID;
        });
    }

    private UUID persistProduct() {
        return transactionTemplate.execute(status -> {
            UUID savedID = productRepository.saveAndFlush(
                    ProductEntity.builder()
                            .sku(PRODUCT_SKU)
                            .name("PRODUCT 1")
                            .description("product 1")
                            .price(new BigDecimal("123.50"))
                            .quantity(INITIAL_PRODUCT_STOCK)
                            .build())
                    .getId();

            productIds.add(savedID);

            return savedID;
        });
    }
}
