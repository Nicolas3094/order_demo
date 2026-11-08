package com.orders.messages.orders_demo.app.order.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerNotFoundException;
import com.orders.messages.orders_demo.app.order.api.CreateOrderRequest;
import com.orders.messages.orders_demo.app.order.internal.exceptions.InvalidOrderStateException;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderNotFoundException;
import com.orders.messages.orders_demo.app.product.internal.ProductEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductRepository;

public class OrderServiceIntegrationTest extends PostgresContainer {
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderService orderService;

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
        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(UUID.randomUUID())
                .currency(Currency.MXN)
                .build();

        assertThatThrownBy(() -> orderService.createOrder(request)).isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    public void shouldCancelOrderAndRestoreProductStock() {
        UUID orderId = persistOrder();
        UUID productId = persistProduct();
        transactionTemplate.executeWithoutResult(status -> orderService.cancelOrder(orderId));

        transactionTemplate.executeWithoutResult(status -> {
            OrderEntity persistedOrder = orderRepository.findById(orderId).orElseThrow();
            ProductEntity persistedProduct = productRepository.findById(productId).orElseThrow();

            assertThat(persistedOrder.getId()).isEqualTo(orderId);
            assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(persistedProduct.getQuantity()).isEqualTo(INITIAL_PRODUCT_STOCK + 1L);
        });
    }

    @Test
    public void shouldExpireOrderAndRestoreProductStock() {
        UUID orderId = persistOrder();
        UUID productId = persistProduct();
        transactionTemplate.executeWithoutResult(status -> orderService.expireOrder(orderId));

        transactionTemplate.executeWithoutResult(status -> {
            OrderEntity persistedOrder = orderRepository.findById(orderId).orElseThrow();
            ProductEntity persistedProduct = productRepository.findById(productId).orElseThrow();

            assertThat(persistedOrder.getId()).isEqualTo(orderId);
            assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.EXPIRED);
            assertThat(persistedProduct.getQuantity()).isEqualTo(INITIAL_PRODUCT_STOCK + 1L);
        });
    }

    @Test
    public void shouldRefundOrderAndRestoreProductStock() {
        UUID orderId = persistOrder(OrderStatus.PAID);
        UUID productId = persistProduct();
        transactionTemplate.executeWithoutResult(status -> orderService.refundOrder(orderId));

        transactionTemplate.executeWithoutResult(status -> {
            OrderEntity persistedOrder = orderRepository.findById(orderId).orElseThrow();
            ProductEntity persistedProduct = productRepository.findById(productId).orElseThrow();

            assertThat(persistedOrder.getId()).isEqualTo(orderId);
            assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.REFUNDED);
            assertThat(persistedProduct.getQuantity()).isEqualTo(INITIAL_PRODUCT_STOCK + 1L);
        });
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PENDING_PAYMENT", mode = EnumSource.Mode.EXCLUDE)
    public void shouldRejectCancellingOrderInInvalidState(OrderStatus orderStatus) {
        UUID orderId = persistOrder(orderStatus);
        UUID productId = persistProduct();

        assertThatThrownBy(() -> orderService.cancelOrder(orderId)).isInstanceOf(InvalidOrderStateException.class);
        transactionTemplate.executeWithoutResult(status -> {
            var order = orderRepository.findById(orderId).orElseThrow();
            var product = productRepository.findById(productId).orElseThrow();

            assertThat(order.getStatus()).isEqualTo(orderStatus);
            assertThat(product.getQuantity()).isEqualTo(INITIAL_PRODUCT_STOCK);
        });
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
                                    .quantity(1L)
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
