package com.orders.messages.orders_demo.app.order.internal;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.order.api.CreateOrderItemRequest;
import com.orders.messages.orders_demo.app.order.internal.exceptions.InvalidOrderStateException;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderItemNotFoundException;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderNotFoundException;
import com.orders.messages.orders_demo.app.product.internal.ProductEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductRepository;
import com.orders.messages.orders_demo.app.product.internal.exceptions.InsufficientStockException;
import com.orders.messages.orders_demo.app.product.internal.exceptions.InvalidProductException;
import com.orders.messages.orders_demo.app.product.internal.exceptions.ProductNotFoundException;

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
            assertThat(item.sku()).isEqualTo(PRODUCT_SKU);
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
        persistProduct();
        CreateOrderItemRequest request = new CreateOrderItemRequest(PRODUCT_SKU, 2L);

        assertThatThrownBy(() -> orderItemService.createOrderItem(missingOrderId, request))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    public void shouldRejectCreatingItemForMissingProduct() {
        UUID orderId = persistOrder();
        CreateOrderItemRequest request = new CreateOrderItemRequest(PRODUCT_SKU, 2L);

        assertThatThrownBy(() -> orderItemService.createOrderItem(orderId, request))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    public void shouldRejectCreatingItemForInactiveProduct() {
        UUID orderId = persistOrder();
        persistInactiveProduct();
        CreateOrderItemRequest request = new CreateOrderItemRequest(PRODUCT_SKU, 2L);

        assertThatThrownBy(() -> orderItemService.createOrderItem(orderId, request))
                .isInstanceOf(InvalidProductException.class);
    }

    @Test
    public void shouldRejectCreatingItemWithDifferentCurrency() {
        UUID orderId = persistOrder();
        persistUSDProduct();
        CreateOrderItemRequest request = new CreateOrderItemRequest(PRODUCT_SKU, 2L);

        assertThatThrownBy(() -> orderItemService.createOrderItem(orderId, request))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    public void shouldRejectCreatingItemWithInsufficientStockWithoutChangingPersistedState() {
        UUID orderId = persistOrder();
        UUID productId = persistProduct();
        CreateOrderItemRequest request = new CreateOrderItemRequest(PRODUCT_SKU, INITIAL_PRODUCT_STOCK + 1);

        assertThatThrownBy(() -> orderItemService.createOrderItem(orderId, request))
                .isInstanceOf(InsufficientStockException.class);
        assertOrderAndProductInitialState(orderId, OrderStatus.PENDING_PAYMENT, productId);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PENDING_PAYMENT", mode = EnumSource.Mode.EXCLUDE)
    public void shouldRejectItemMutationsWhenOrderIsNotPending(OrderStatus state) {
        UUID orderId = persistOrder(state);
        UUID productId = persistProduct();
        UUID orderItemIdFromOrder = getFirstOrderItem(orderId);

        assertThatThrownBy(
                () -> orderItemService.createOrderItem(orderId, new CreateOrderItemRequest(PRODUCT_SKU, 1L)))
                .isInstanceOf(InvalidOrderStateException.class);
        assertThatThrownBy(
                () -> orderItemService.changeUnitPrice(orderId, orderItemIdFromOrder, new BigDecimal("200.00")))
                .isInstanceOf(InvalidOrderStateException.class);
        assertThatThrownBy(
                () -> orderItemService.changeQuantity(orderId, orderItemIdFromOrder, 2L))
                .isInstanceOf(InvalidOrderStateException.class);
        assertThatThrownBy(
                () -> orderItemService.deleteOrderItem(orderId, orderItemIdFromOrder))
                .isInstanceOf(InvalidOrderStateException.class);
        assertOrderProductAndItemInitialState(orderId, orderItemIdFromOrder, state, productId);
    }

    @Test
    public void shouldChangeUnitPriceAndUpdateLineAndOrderTotalsWithoutChangingStock() {
        UUID orderId = persistOrder();
        UUID productId = persistProduct();
        UUID orderItemIdFromOrder = getFirstOrderItem(orderId);

        transactionTemplate.executeWithoutResult(
                (state) -> orderItemService.changeUnitPrice(orderId, orderItemIdFromOrder, new BigDecimal("200.00")));
        transactionTemplate.executeWithoutResult(status -> {
            OrderEntity persistedOrder = orderRepository.findById(orderId).orElseThrow();
            ProductEntity persistedProduct = productRepository.findById(productId).orElseThrow();
            OrderItemEntity orderItemEntity = orderItemRepository.findById(orderItemIdFromOrder).orElseThrow();

            assertThat(persistedOrder.getId()).isEqualTo(orderId);
            assertThat(persistedProduct.getId()).isEqualTo(productId);
            assertThat(orderItemEntity.getId()).isEqualTo(orderItemIdFromOrder);
            assertThat(orderItemEntity.getUnitPrice()).isEqualByComparingTo(new BigDecimal("200.00"));
            assertThat(orderItemEntity.getLineTotal()).isEqualByComparingTo(new BigDecimal("200.00"));
            assertThat(persistedOrder.getAmountTotal()).isEqualByComparingTo(new BigDecimal("200.00"));
            assertThat(persistedProduct.getQuantity()).isEqualTo(INITIAL_PRODUCT_STOCK);
        });
    }

    @Test
    public void shouldIncreaseQuantityDecreaseStockAndUpdateTotals() {
        UUID orderId = persistOrder();
        UUID productId = persistProduct();
        UUID orderItemIdFromOrder = getFirstOrderItem(orderId);

        transactionTemplate.executeWithoutResult(
                (state) -> orderItemService.changeQuantity(orderId, orderItemIdFromOrder, 5L));
        transactionTemplate.executeWithoutResult(status -> {
            OrderEntity persistedOrder = orderRepository.findById(orderId).orElseThrow();
            ProductEntity persistedProduct = productRepository.findById(productId).orElseThrow();
            OrderItemEntity orderItemEntity = orderItemRepository.findById(orderItemIdFromOrder).orElseThrow();

            assertThat(persistedOrder.getId()).isEqualTo(orderId);
            assertThat(persistedProduct.getId()).isEqualTo(productId);
            assertThat(orderItemEntity.getId()).isEqualTo(orderItemIdFromOrder);
            assertThat(orderItemEntity.getQuantity()).isEqualTo(5L);
            assertThat(orderItemEntity.getLineTotal()).isEqualByComparingTo(new BigDecimal("617.50"));
            assertThat(persistedOrder.getAmountTotal()).isEqualByComparingTo(new BigDecimal("617.50"));
            assertThat(persistedProduct.getQuantity()).isEqualTo(6L);
        });
    }

    @Test
    public void shouldDecreaseQuantityRestoreStockAndUpdateTotals() {
        UUID orderId = persistOrder(/* quantity= */ 2L);
        UUID productId = persistProduct();
        UUID orderItemIdFromOrder = getFirstOrderItem(orderId);

        transactionTemplate.executeWithoutResult(
                (state) -> orderItemService.changeQuantity(orderId, orderItemIdFromOrder, 1L));
        transactionTemplate.executeWithoutResult(status -> {
            OrderEntity persistedOrder = orderRepository.findById(orderId).orElseThrow();
            ProductEntity persistedProduct = productRepository.findById(productId).orElseThrow();
            OrderItemEntity orderItemEntity = orderItemRepository.findById(orderItemIdFromOrder).orElseThrow();

            assertThat(persistedOrder.getId()).isEqualTo(orderId);
            assertThat(persistedProduct.getId()).isEqualTo(productId);
            assertThat(orderItemEntity.getId()).isEqualTo(orderItemIdFromOrder);
            assertThat(orderItemEntity.getQuantity()).isEqualTo(1L);
            assertThat(orderItemEntity.getLineTotal()).isEqualByComparingTo(new BigDecimal("123.50"));
            assertThat(persistedOrder.getAmountTotal()).isEqualByComparingTo(new BigDecimal("123.50"));
            assertThat(persistedProduct.getQuantity()).isEqualTo(INITIAL_PRODUCT_STOCK + 1L);
        });
    }

    @Test
    public void shouldPreserveStockAndTotalsWhenQuantityIsUnchanged() {
        UUID orderId = persistOrder(/* quantity= */ 1L);
        UUID productId = persistProduct();
        UUID orderItemIdFromOrder = getFirstOrderItem(orderId);

        transactionTemplate.executeWithoutResult(
                (state) -> orderItemService.changeQuantity(orderId, orderItemIdFromOrder, 1L));
        transactionTemplate.executeWithoutResult(status -> {
            OrderEntity persistedOrder = orderRepository.findById(orderId).orElseThrow();
            ProductEntity persistedProduct = productRepository.findById(productId).orElseThrow();
            OrderItemEntity orderItemEntity = orderItemRepository.findById(orderItemIdFromOrder).orElseThrow();

            assertThat(persistedOrder.getId()).isEqualTo(orderId);
            assertThat(persistedProduct.getId()).isEqualTo(productId);
            assertThat(orderItemEntity.getId()).isEqualTo(orderItemIdFromOrder);
            assertThat(orderItemEntity.getQuantity()).isEqualTo(1L);
            assertThat(orderItemEntity.getLineTotal()).isEqualByComparingTo(new BigDecimal("123.50"));
            assertThat(persistedOrder.getAmountTotal()).isEqualByComparingTo(new BigDecimal("123.50"));
            assertThat(persistedProduct.getQuantity()).isEqualTo(INITIAL_PRODUCT_STOCK);
        });
    }

    @Test
    public void shouldRejectIncreasingQuantityWithInsufficientStockWithoutChangingPersistedState() {
        UUID orderId = persistOrder(/* quantity= */ 1L);
        UUID productId = persistProduct(/* stock= */ 2L);
        UUID orderItemIdFromOrder = getFirstOrderItem(orderId);

        assertThatThrownBy(
                () -> orderItemService.changeQuantity(orderId, orderItemIdFromOrder, 4L))
                .isInstanceOf(InsufficientStockException.class);
        transactionTemplate.executeWithoutResult(status -> {
            OrderEntity persistedOrder = orderRepository.findById(orderId).orElseThrow();
            ProductEntity persistedProduct = productRepository.findById(productId).orElseThrow();
            OrderItemEntity orderItemEntity = orderItemRepository.findById(orderItemIdFromOrder).orElseThrow();

            assertThat(persistedOrder.getId()).isEqualTo(orderId);
            assertThat(persistedOrder.getItems().size()).isEqualTo(1);
            assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(persistedOrder.getAmountTotal()).isEqualByComparingTo(new BigDecimal("123.50"));
            assertThat(persistedProduct.getQuantity()).isEqualTo(2);
            assertThat(orderItemEntity.getQuantity()).isEqualTo(1);
            assertThat(orderItemEntity.getLineTotal()).isEqualByComparingTo(new BigDecimal("123.50"));
        });
    }

    @Test
    public void shouldDeleteItemRestoreStockAndUpdateOrderTotal() {
        UUID orderId = persistOrder();
        UUID productId = persistProduct();
        UUID orderItemIdFromOrder = getFirstOrderItem(orderId);

        transactionTemplate.executeWithoutResult(
                (state) -> orderItemService.deleteOrderItem(orderId, orderItemIdFromOrder));
        transactionTemplate.executeWithoutResult(status -> {
            OrderEntity persistedOrder = orderRepository.findById(orderId).orElseThrow();
            ProductEntity persistedProduct = productRepository.findById(productId).orElseThrow();

            assertThat(orderItemRepository.findById(orderItemIdFromOrder)).isEmpty();
            assertThat(persistedOrder.getItems().size()).isEqualTo(0);
            assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(persistedOrder.getAmountTotal()).isEqualByComparingTo(new BigDecimal("0.00"));
            assertThat(persistedProduct.getQuantity()).isEqualTo(INITIAL_PRODUCT_STOCK + 1);
        });
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

    private UUID persistOrder(OrderStatus state) {
        return transactionTemplate.execute(status -> {
            UUID customerId = customerRepository
                    .saveAndFlush(new CustomerEntity(UUID.randomUUID() + "@example.com", "name")).getId();

            customerIds.add(customerId);

            var savedID = orderRepository.saveAndFlush(
                    OrderEntity.builder()
                            .status(state)
                            .customerId(customerId)
                            .addItem(OrderItemEntity.builder()
                                    .sku(PRODUCT_SKU)
                                    .description("order item 1")
                                    .unitPrice(new BigDecimal("123.50"))
                                    .quantity(1L)
                                    .build())
                            .build())
                    .getId();

            orderIds.add(savedID);

            return savedID;
        });
    }

    private UUID persistOrder() {
        return persistOrder(1L);
    }

    private UUID persistOrder(long quantity) {
        return transactionTemplate.execute(status -> {
            UUID customerId = customerRepository
                    .saveAndFlush(new CustomerEntity(UUID.randomUUID() + "@example.com", "name")).getId();

            customerIds.add(customerId);

            var savedID = orderRepository.saveAndFlush(
                    OrderEntity.builder()
                            .customerId(customerId)
                            .addItem(OrderItemEntity.builder()
                                    .sku(PRODUCT_SKU)
                                    .description("order item 1")
                                    .unitPrice(new BigDecimal("123.50"))
                                    .quantity(quantity)
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

    private UUID persistProduct(Long stock) {
        return transactionTemplate.execute(status -> {
            UUID savedID = productRepository.saveAndFlush(
                    ProductEntity.builder()
                            .sku(PRODUCT_SKU)
                            .name("PRODUCT 1")
                            .description("product 1")
                            .price(new BigDecimal("123.50"))
                            .quantity(stock)
                            .build())
                    .getId();

            productIds.add(savedID);

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

    private UUID persistUSDProduct() {
        return transactionTemplate.execute(status -> {
            UUID savedID = productRepository.saveAndFlush(
                    ProductEntity.builder()
                            .sku(PRODUCT_SKU)
                            .name("PRODUCT 1")
                            .description("product 1")
                            .price(new BigDecimal("123.50"))
                            .quantity(INITIAL_PRODUCT_STOCK)
                            .currency(Currency.USD)
                            .build())
                    .getId();

            productIds.add(savedID);

            return savedID;
        });
    }

    private UUID persistInactiveProduct() {
        return transactionTemplate.execute(status -> {
            UUID savedID = productRepository.saveAndFlush(
                    ProductEntity.builder()
                            .active(false)
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

    private void assertOrderAndProductInitialState(UUID orderId, OrderStatus expectedOrderStatus, UUID productId) {
        transactionTemplate.executeWithoutResult(status -> {
            OrderEntity persistedOrder = orderRepository.findById(orderId).orElseThrow();
            ProductEntity persistedProduct = productRepository.findById(productId).orElseThrow();

            assertThat(persistedOrder.getId()).isEqualTo(orderId);
            assertThat(persistedOrder.getItems().size()).isEqualTo(1);
            assertThat(persistedOrder.getStatus()).isEqualTo(expectedOrderStatus);
            assertThat(persistedOrder.getAmountTotal()).isEqualByComparingTo(new BigDecimal("123.50"));
            assertThat(persistedProduct.getQuantity()).isEqualTo(INITIAL_PRODUCT_STOCK);
        });
    }

    private void assertOrderProductAndItemInitialState(UUID orderId, UUID orderItemId, OrderStatus expectedOrderStatus,
            UUID productId) {
        transactionTemplate.executeWithoutResult(status -> {
            OrderEntity persistedOrder = orderRepository.findById(orderId).orElseThrow();
            ProductEntity persistedProduct = productRepository.findById(productId).orElseThrow();
            OrderItemEntity orderItemEntity = orderItemRepository.findById(orderItemId).orElseThrow();

            assertThat(orderItemEntity.getId()).isEqualTo(orderItemId);
            assertThat(orderItemEntity.getQuantity()).isEqualTo(1);
            assertThat(orderItemEntity.getUnitPrice()).isEqualByComparingTo(new BigDecimal("123.50"));
            assertThat(persistedOrder.getId()).isEqualTo(orderId);
            assertThat(persistedOrder.getItems().size()).isEqualTo(1);
            assertThat(persistedOrder.getStatus()).isEqualTo(expectedOrderStatus);
            assertThat(persistedOrder.getAmountTotal()).isEqualByComparingTo(new BigDecimal("123.50"));
            assertThat(persistedProduct.getQuantity()).isEqualTo(INITIAL_PRODUCT_STOCK);
        });
    }
}
