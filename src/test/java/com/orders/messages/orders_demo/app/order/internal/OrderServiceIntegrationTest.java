package com.orders.messages.orders_demo.app.order.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
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

// PER_CLASS permite que @MethodSource use un método no estático y acceda al servicio inyectado.
// Con PER_METHOD (el valor por defecto), la fuente debe ser estática; puede devolver
// Consumer<OrderService> y recibir orderService al ejecutar cada test.
// PER_CLASS comparte la instancia y sus listas de IDs entre todas las pruebas.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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

        orderIds.clear();
        customerIds.clear();
        productIds.clear();
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

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = { "PENDING_PAYMENT", "PAYMENT_IN_PROGRESS" })
    void shouldReuseOpenOrderWithoutChangingItemsTotalOrVersion(OrderStatus state) {
        UUID orderId = persistOrder(state);
        var before = transactionTemplate.execute(status -> orderRepository.findById(orderId).orElseThrow());
        var request = openOrderRequest(before.getCustomerId(), Currency.MXN);

        var result = orderService.createOrReuseOrder(request);

        assertThat(result.created()).isFalse();
        assertThat(result.order().id()).isEqualTo(orderId);
        transactionTemplate.executeWithoutResult(status -> {
            var orders = orderRepository.findByCustomerId(before.getCustomerId(),
                    org.springframework.data.domain.Pageable.unpaged());
            assertThat(orders).hasSize(1);
            var persisted = orders.getContent().get(0);
            assertThat(persisted.getStatus()).isEqualTo(state);
            assertThat(persisted.getVersion()).isEqualTo(before.getVersion());
            assertThat(persisted.getAmountTotal()).isEqualByComparingTo(before.getAmountTotal());
            assertThat(persisted.getItems()).hasSize(1);
            assertThat(persisted.getItems().get(0).getQuantity()).isEqualTo(1L);
        });
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = { "PENDING_PAYMENT", "PAYMENT_IN_PROGRESS" })
    void shouldRejectDifferentCurrencyWithoutChangingOpenOrder(OrderStatus state) {
        UUID orderId = persistOrder(state);
        var before = transactionTemplate.execute(status -> orderRepository.findById(orderId).orElseThrow());

        assertThatThrownBy(
                () -> orderService.createOrReuseOrder(openOrderRequest(before.getCustomerId(), Currency.USD)))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessage("The open order uses a different currency.");

        var orders = orderRepository.findByCustomerId(before.getCustomerId(),
                org.springframework.data.domain.Pageable.unpaged());
        assertThat(orders).hasSize(1);
        assertThat(orders.getContent().get(0).getCurrency()).isEqualTo(Currency.MXN);
        assertThat(orders.getContent().get(0).getStatus()).isEqualTo(state);
        assertThat(orders.getContent().get(0).getVersion()).isEqualTo(before.getVersion());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = { "PAID", "CANCELLED", "EXPIRED", "REFUNDED" })
    void shouldCreateNewOrderWhenOnlyHistoricalOrderExists(OrderStatus state) {
        UUID historicalId = persistOrder(state);
        UUID customerId = orderRepository.findById(historicalId).orElseThrow().getCustomerId();

        var result = orderService.createOrReuseOrder(openOrderRequest(customerId, Currency.MXN));
        orderIds.add(result.order().id());

        assertThat(result.created()).isTrue();
        assertThat(result.order().id()).isNotEqualTo(historicalId);
        assertThat(result.order().status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(orderRepository.findById(historicalId).orElseThrow().getStatus()).isEqualTo(state);
    }

    @Test
    void shouldCreateOneOpenOrderForConcurrentRequestsFromSameCustomer() throws Exception {
        UUID customerId = persistCustomer();
        var request = openOrderRequest(customerId, Currency.MXN);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            java.util.concurrent.Callable<OrderService.OrderCreationResult> action = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent start timed out");
                }
                return orderService.createOrReuseOrder(request);
            };
            var first = executor.submit(action);
            var second = executor.submit(action);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));

            assertThat(results).extracting(result -> result.order().id()).containsOnly(results.get(0).order().id());
            assertThat(results).extracting(OrderService.OrderCreationResult::created).containsExactlyInAnyOrder(true,
                    false);
            var persisted = orderRepository.findByCustomerId(customerId,
                    org.springframework.data.domain.Pageable.unpaged());
            assertThat(persisted).hasSize(1);
            assertThat(persisted.getContent().get(0).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            orderRepository.findByCustomerId(customerId, org.springframework.data.domain.Pageable.unpaged())
                    .forEach(order -> orderIds.add(order.getId()));
        }
    }

    @Test
    void shouldCreateIndependentOpenOrdersForDifferentCustomers() {
        UUID firstCustomer = persistCustomer();
        UUID secondCustomer = persistCustomer();

        var first = orderService.createOrReuseOrder(openOrderRequest(firstCustomer, Currency.MXN));
        orderIds.add(first.order().id());
        var second = orderService.createOrReuseOrder(openOrderRequest(secondCustomer, Currency.MXN));
        orderIds.add(second.order().id());

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isTrue();
        assertThat(first.order().id()).isNotEqualTo(second.order().id());
    }

    private CreateOrderRequest openOrderRequest(UUID customerId, Currency currency) {
        return CreateOrderRequest.builder().customerId(customerId).currency(currency).build();
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
    public void shouldCancelOrderWithoutChangingProductStock() {
        UUID orderId = persistOrder();
        UUID productId = persistProduct();
        transactionTemplate.executeWithoutResult(status -> orderService.cancelOrder(orderId));

        transactionTemplate.executeWithoutResult(status -> {
            OrderEntity persistedOrder = orderRepository.findById(orderId).orElseThrow();
            ProductEntity persistedProduct = productRepository.findById(productId).orElseThrow();

            assertThat(persistedOrder.getId()).isEqualTo(orderId);
            assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(persistedProduct.getQuantity()).isEqualTo(INITIAL_PRODUCT_STOCK);
        });
    }

    @Test
    public void shouldExpireOrderWithoutChangingProductStock() {
        UUID orderId = persistOrder();
        UUID productId = persistProduct();
        transactionTemplate.executeWithoutResult(status -> orderService.expireOrder(orderId));

        transactionTemplate.executeWithoutResult(status -> {
            OrderEntity persistedOrder = orderRepository.findById(orderId).orElseThrow();
            ProductEntity persistedProduct = productRepository.findById(productId).orElseThrow();

            assertThat(persistedOrder.getId()).isEqualTo(orderId);
            assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.EXPIRED);
            assertThat(persistedProduct.getQuantity()).isEqualTo(INITIAL_PRODUCT_STOCK);
        });
    }

    @Test
    public void shouldRefundOrderWithoutChangingProductStock() {
        UUID orderId = persistOrder(OrderStatus.PAID);
        UUID productId = persistProduct();
        transactionTemplate.executeWithoutResult(status -> orderService.refundOrder(orderId));

        transactionTemplate.executeWithoutResult(status -> {
            OrderEntity persistedOrder = orderRepository.findById(orderId).orElseThrow();
            ProductEntity persistedProduct = productRepository.findById(productId).orElseThrow();

            assertThat(persistedOrder.getId()).isEqualTo(orderId);
            assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.REFUNDED);
            assertThat(persistedProduct.getQuantity()).isEqualTo(INITIAL_PRODUCT_STOCK);
        });
    }

    @Test
    public void shouldPersistOrderStatusAsPaymentInProgress() {
        UUID orderId = persistOrder();
        UUID productId = persistProduct();

        orderService.paymentInProgress(orderId);

        transactionTemplate.executeWithoutResult(status -> {
            OrderEntity persistedOrder = orderRepository.findById(orderId).orElseThrow();
            ProductEntity persistedProduct = productRepository.findById(productId).orElseThrow();

            assertThat(persistedOrder.getId()).isEqualTo(orderId);
            assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
            assertThat(persistedProduct.getQuantity()).isEqualTo(INITIAL_PRODUCT_STOCK);
        });
    }

    @Test
    public void shouldRejectOnMissingOrderWhenSettingPayment() {
        UUID nonExistentOrderId = UUID.randomUUID();

        assertThatThrownBy(() -> orderService.paymentInProgress(nonExistentOrderId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order could not be found.");
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PENDING_PAYMENT", mode = EnumSource.Mode.EXCLUDE)
    public void shouldRejectCancellingOrderInInvalidState(OrderStatus orderStatus) {
        UUID orderId = persistOrder(orderStatus);
        UUID productId = persistProduct();

        assertThatThrownBy(() -> orderService.cancelOrder(orderId)).isInstanceOf(InvalidOrderStateException.class);
        assertOrderAndProductState(orderId, orderStatus, productId);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PENDING_PAYMENT", mode = EnumSource.Mode.EXCLUDE)
    public void shouldRejectExpiringOrderInInvalidState(OrderStatus orderStatus) {
        UUID orderId = persistOrder(orderStatus);
        UUID productId = persistProduct();

        assertThatThrownBy(() -> orderService.expireOrder(orderId)).isInstanceOf(InvalidOrderStateException.class);
        assertOrderAndProductState(orderId, orderStatus, productId);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PAID", mode = EnumSource.Mode.EXCLUDE)
    public void shouldRejectRefundingOrderInInvalidState(OrderStatus orderStatus) {
        UUID orderId = persistOrder(orderStatus);
        UUID productId = persistProduct();

        assertThatThrownBy(() -> orderService.refundOrder(orderId)).isInstanceOf(InvalidOrderStateException.class);
        assertOrderAndProductState(orderId, orderStatus, productId);
    }

    @ParameterizedTest
    @MethodSource("stateChanges")
    public void shouldRejectStateChangesForMissingOrder(Consumer<UUID> operation) {
        UUID missingOrderId = UUID.randomUUID();

        assertThatThrownBy(() -> operation.accept(missingOrderId)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void shouldCompleteCancellationWithoutLookingUpMissingProducts() {
        UUID orderId = persistOrderWithUnknownSKU();
        UUID productId = persistProduct();

        orderService.cancelOrder(orderId);

        assertOrderAndProductState(orderId, OrderStatus.CANCELLED, productId);
    }

    @Test
    void shouldCompleteExpirationWithoutLookingUpMissingProducts() {
        UUID orderId = persistOrderWithUnknownSKU();
        UUID productId = persistProduct();

        orderService.expireOrder(orderId);

        assertOrderAndProductState(orderId, OrderStatus.EXPIRED, productId);
    }

    @Test
    void shouldCompleteRefundWithoutLookingUpMissingProducts() {
        UUID orderId = persistOrderWithUnknownSKU(OrderStatus.PAID);
        UUID productId = persistProduct();

        orderService.refundOrder(orderId);

        assertOrderAndProductState(orderId, OrderStatus.REFUNDED, productId);
    }

    // Cada Consumer recibe el UUID inexistente y ejecuta una transición en el
    // servicio real.
    private Stream<Consumer<UUID>> stateChanges() {
        return Stream.of(
                orderService::cancelOrder,
                orderService::expireOrder,
                orderService::refundOrder);
    }

    private void assertOrderAndProductState(UUID orderId, OrderStatus expectedOrderStatus, UUID productId) {
        transactionTemplate.executeWithoutResult(status -> {
            var order = orderRepository.findById(orderId).orElseThrow();
            var product = productRepository.findById(productId).orElseThrow();

            assertThat(order.getStatus()).isEqualTo(expectedOrderStatus);
            assertThat(product.getQuantity()).isEqualTo(INITIAL_PRODUCT_STOCK);
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

    private UUID persistOrderWithUnknownSKU(OrderStatus state) {
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
                            .addItem(OrderItemEntity.builder()
                                    .sku("UNKNOWN-SKU")
                                    .description("order item 2")
                                    .unitPrice(new BigDecimal("123.50"))
                                    .quantity(1L)
                                    .build())
                            .build())
                    .getId();

            orderIds.add(savedID);

            return savedID;
        });
    }

    private UUID persistOrderWithUnknownSKU() {
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
                            .addItem(OrderItemEntity.builder()
                                    .sku("UNKNOWN-SKU")
                                    .description("order item 2")
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
