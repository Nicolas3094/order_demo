package com.orders.messages.orders_demo.app.payment.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;

import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.order.internal.OrderEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderItemEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderRepository;
import com.orders.messages.orders_demo.app.order.internal.OrderStatus;
import com.orders.messages.orders_demo.app.payment.api.CreatePaymentAttemptRequest;
import com.orders.messages.orders_demo.app.product.internal.ProductEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductRepository;
import com.orders.messages.orders_demo.app.product.internal.exceptions.InvalidProductException;
import com.orders.messages.orders_demo.app.product.internal.exceptions.InsufficientStockException;

class PaymentStockReservationIntegrationTest extends PostgresContainer {
    @Autowired
    private PaymentAttemptService paymentService;
    @Autowired
    private PaymentAttemptRepository paymentRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<UUID> orderIds = new ArrayList<>();
    private final List<UUID> customerIds = new ArrayList<>();
    private final List<UUID> productIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            orderIds.forEach(id -> paymentRepository.deleteAll(paymentRepository.findByOrderId(id)));
            paymentRepository.flush();
            orderIds.forEach(orderRepository::deleteById);
            orderRepository.flush();
            customerIds.forEach(customerRepository::deleteById);
            customerRepository.flush();
            productIds.forEach(productRepository::deleteById);
            productRepository.flush();
        });
    }

    @Test
    void shouldReserveStockOnceWhenPaymentCreationIsRetriedWithSameKey() {
        UUID productId = persistProduct("SKU-A", 5L);
        UUID orderId = persistOrder("SKU-A", 2L);
        var createPaymentRequest = createPaymentRequest();

        var firstPaymentResponse = paymentService.createPaymentAttempt(orderId, createPaymentRequest);
        var repeatedPaymentResponse = paymentService.createPaymentAttempt(orderId, createPaymentRequest);

        assertThat(repeatedPaymentResponse.id()).isEqualTo(firstPaymentResponse.id());
        assertThat(readAvailableStock(productId)).isEqualTo(3L);
        assertThat(paymentRepository.findByOrderId(orderId)).hasSize(1);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
    }

    @Test
    void shouldRejectCheckoutWhenStockWasConsumedAfterAddingItems() {
        UUID productId = persistProduct("SKU-A", 1L);
        UUID orderId = persistOrder("SKU-A", 2L);

        assertThatThrownBy(() -> paymentService.createPaymentAttempt(orderId, createPaymentRequest()))
                .isInstanceOf(InvalidProductException.class);

        assertThat(readAvailableStock(productId)).isEqualTo(1L);
        assertThat(paymentRepository.findByOrderId(orderId)).isEmpty();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void shouldRollBackAllReservationsWhenOneProductHasInsufficientStock() {
        UUID firstProductId = persistProduct("SKU-A", 5L);
        UUID secondProductId = persistProduct("SKU-B", 1L);
        UUID orderId = persistOrder("SKU-A", 2L);
        transactionTemplate.executeWithoutResult(status -> {
            var persistedOrder = orderRepository.findById(orderId).orElseThrow();
            persistedOrder.addItem(createOrderItem("SKU-B", 2L));
            orderRepository.saveAndFlush(persistedOrder);
        });

        assertThatThrownBy(() -> paymentService.createPaymentAttempt(orderId, createPaymentRequest()))
                .isInstanceOf(InvalidProductException.class);

        assertThat(readAvailableStock(firstProductId)).isEqualTo(5L);
        assertThat(readAvailableStock(secondProductId)).isEqualTo(1L);
        assertThat(paymentRepository.findByOrderId(orderId)).isEmpty();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void shouldRollBackStockReservationWhenCallerFails() {
        UUID productId = persistProduct("SKU-A", 5L);
        UUID orderId = persistOrder("SKU-A", 2L);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            paymentService.createPaymentAttempt(orderId, createPaymentRequest());
            entityManager.flush();
            entityManager.clear();
            assertThat(readAvailableStock(productId)).isEqualTo(3L);
            throw new IllegalStateException("Caller failed");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Caller failed");

        assertThat(readAvailableStock(productId)).isEqualTo(5L);
        assertThat(paymentRepository.findByOrderId(orderId)).isEmpty();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void shouldReleaseReservationOnFailure() {
        // Existing active attempt with two units already reserved: available stock is
        // three.
        UUID productId = persistProduct("SKU-A", 3L);
        UUID orderId = persistOrder("SKU-A", 2L);
        UUID paymentId = persistReservedPayment(orderId);

        paymentService.markAsFailed(orderId, paymentId, 402, "Declined");
        paymentService.markAsFailed(orderId, paymentId, 402, "Declined");

        assertThat(readAvailableStock(productId)).isEqualTo(5L);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void shouldReleaseReservationOnCancellation() {
        // Existing active attempt with two units already reserved: available stock is
        // three.
        UUID productId = persistProduct("SKU-A", 3L);
        UUID orderId = persistOrder("SKU-A", 2L);
        UUID paymentId = persistReservedPayment(orderId);

        paymentService.markAsCancelled(orderId, paymentId);

        assertThat(readAvailableStock(productId)).isEqualTo(5L);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void shouldKeepReservationOnSuccess() {
        // Existing active attempt with two units already reserved: available stock is
        // three.
        UUID productId = persistProduct("SKU-A", 3L);
        UUID orderId = persistOrder("SKU-A", 2L);
        UUID paymentId = persistReservedPayment(orderId);

        paymentService.markAsSucceeded(orderId, paymentId, "ref-stock");
        paymentService.markAsSucceeded(orderId, paymentId, "ref-stock");

        assertThat(readAvailableStock(productId)).isEqualTo(3L);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void shouldReserveEveryProductAndUsePersistedOrderTotal() {
        UUID firstProductId = persistProduct("SKU-FIRST", 5L);
        UUID secondProductId = persistProduct("SKU-SECOND", 7L);
        UUID orderId = persistOrder("SKU-FIRST", 2L);
        transactionTemplate.executeWithoutResult(status -> {
            var persistedOrder = orderRepository.findById(orderId).orElseThrow();
            persistedOrder.addItem(createOrderItem("SKU-SECOND", 3L));
            orderRepository.saveAndFlush(persistedOrder);
        });
        var createPaymentRequest = createPaymentRequest();

        var createdPaymentResponse = paymentService.createPaymentAttempt(orderId, createPaymentRequest);

        assertThat(createdPaymentResponse.amount()).isEqualByComparingTo("500.00");
        assertThat(readAvailableStock(firstProductId)).isEqualTo(3L);
        assertThat(readAvailableStock(secondProductId)).isEqualTo(4L);
        assertThat(paymentRepository.findByOrderId(orderId)).hasSize(1);
    }

    @Test
    void shouldRollBackReservationAndOrderStateWhenSavingPaymentFails() {
        UUID productId = persistProduct("SKU-SAVE-FAILURE", 5L);
        UUID orderId = persistOrder("SKU-SAVE-FAILURE", 2L);
        // The database column is limited to 64 characters; force failure after
        // reservation.
        var createPaymentRequest = new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, "K".repeat(65));
        Long initialOrderVersion = orderRepository.findById(orderId).orElseThrow().getVersion();

        assertThatThrownBy(() -> paymentService.createPaymentAttempt(orderId, createPaymentRequest))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(readAvailableStock(productId)).isEqualTo(5L);
        assertThat(paymentRepository.findByOrderId(orderId)).isEmpty();
        var persistedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(persistedOrder.getVersion()).isEqualTo(initialOrderVersion);
    }

    @Test
    void shouldAllowOnlyOneCheckoutWhenTwoOrdersCompeteForInsufficientSharedStock() throws Exception {
        // Un producto con tres unidades y dos carritos que quieren dos cada uno.
        UUID productId = persistProduct("SKU-CONCURRENT", 3L);
        UUID firstOrderId = persistOrder("SKU-CONCURRENT", 2L);
        UUID secondOrderId = persistOrder("SKU-CONCURRENT", 2L);
        var productReadBarrier = new CyclicBarrier(2);
        var checkoutExecutor = Executors.newFixedThreadPool(2);

        try {
            // Cada tarea simula un cliente distinto intentando pagar.
            var firstCheckoutFuture = checkoutExecutor.submit(
                    () -> tryCreatingPayment(productId, firstOrderId, productReadBarrier));
            var secondCheckoutFuture = checkoutExecutor.submit(
                    () -> tryCreatingPayment(productId, secondOrderId, productReadBarrier));

            boolean firstCheckoutSucceeded = firstCheckoutFuture.get(20, TimeUnit.SECONDS);
            boolean secondCheckoutSucceeded = secondCheckoutFuture.get(20, TimeUnit.SECONDS);

            // No importa cuál ganó: una compra debe funcionar y la otra debe rechazarse.
            assertThat(List.of(firstCheckoutSucceeded, secondCheckoutSucceeded))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(readAvailableStock(productId)).isEqualTo(1L);

            var persistedOrders = orderRepository.findAllById(List.of(firstOrderId, secondOrderId));
            assertThat(persistedOrders).extracting(OrderEntity::getStatus)
                    .containsExactlyInAnyOrder(OrderStatus.PAYMENT_IN_PROGRESS, OrderStatus.PENDING_PAYMENT);

            var persistedPayments = new ArrayList<>(paymentRepository.findByOrderId(firstOrderId));
            persistedPayments.addAll(paymentRepository.findByOrderId(secondOrderId));
            assertThat(persistedPayments).hasSize(1);
            var persistedPayment = persistedPayments.get(0);
            assertThat(persistedPayment.getAmount()).isEqualByComparingTo("200.00");
            assertThat(orderRepository.findById(persistedPayment.getOrderId()).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
        } finally {
            checkoutExecutor.shutdownNow();
            assertThat(checkoutExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void shouldAllowBothCheckoutsWhenSharedStockIsEnough() throws Exception {
        UUID productId = persistProduct("SKU-CONCURRENT-ENOUGH", 4L);
        UUID firstOrderId = persistOrder("SKU-CONCURRENT-ENOUGH", 2L);
        UUID secondOrderId = persistOrder("SKU-CONCURRENT-ENOUGH", 2L);
        var productReadBarrier = new CyclicBarrier(2);
        var checkoutExecutor = Executors.newFixedThreadPool(2);

        try {
            var firstCheckoutFuture = checkoutExecutor.submit(
                    () -> tryCreatingPayment(productId, firstOrderId, productReadBarrier));
            var secondCheckoutFuture = checkoutExecutor.submit(
                    () -> tryCreatingPayment(productId, secondOrderId, productReadBarrier));

            assertThat(firstCheckoutFuture.get(20, TimeUnit.SECONDS)).isTrue();
            assertThat(secondCheckoutFuture.get(20, TimeUnit.SECONDS)).isTrue();
            assertThat(readAvailableStock(productId)).isZero();
            assertThat(paymentRepository.findByOrderId(firstOrderId)).hasSize(1);
            assertThat(paymentRepository.findByOrderId(secondOrderId)).hasSize(1);
            assertThat(orderRepository.findAllById(List.of(firstOrderId, secondOrderId)))
                    .extracting(OrderEntity::getStatus)
                    .containsExactly(OrderStatus.PAYMENT_IN_PROGRESS, OrderStatus.PAYMENT_IN_PROGRESS);
        } finally {
            checkoutExecutor.shutdownNow();
            assertThat(checkoutExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private boolean tryCreatingPayment(UUID productId, UUID orderId, CyclicBarrier productReadBarrier) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                var initialProduct = productRepository.findById(productId).orElseThrow();
                assertThat(initialProduct.getQuantity()).isGreaterThanOrEqualTo(2L);

                // Ninguno continúa hasta que ambos hayan leído las existencias iniciales.
                awaitProductReadBarrier(productReadBarrier);

                paymentService.createPaymentAttempt(orderId, createPaymentRequest());
            });
            return true;
        } catch (InsufficientStockException reservationException) {
            return false;
        }
    }

    private void awaitProductReadBarrier(CyclicBarrier productReadBarrier) {
        try {
            productReadBarrier.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent checkout interrupted", interruptedException);
        } catch (Exception synchronizationException) {
            throw new IllegalStateException("Could not synchronize product reads", synchronizationException);
        }
    }

    private UUID persistReservedPayment(UUID orderId) {
        return transactionTemplate.execute(status -> {
            var persistedOrder = orderRepository.findById(orderId).orElseThrow();
            persistedOrder.paymentInProgress();
            orderRepository.saveAndFlush(persistedOrder);
            return paymentRepository.saveAndFlush(PaymentAttemptEntity.builder()
                    .orderId(orderId).amount(new BigDecimal("200.00"))
                    .status(PaymentStatus.PROCESSING).provider(PaymentProvider.STRIPE).build()).getId();
        });
    }

    private UUID persistProduct(String sku, long stock) {
        UUID id = productRepository.saveAndFlush(ProductEntity.builder().sku(sku).name("Product")
                .price(new BigDecimal("100.00")).quantity(stock).build()).getId();
        productIds.add(id);
        return id;
    }

    private UUID persistOrder(String sku, long quantity) {
        UUID customerId = customerRepository
                .saveAndFlush(new CustomerEntity(UUID.randomUUID() + "@example.com", "Customer")).getId();
        customerIds.add(customerId);
        UUID id = orderRepository.saveAndFlush(OrderEntity.builder().customerId(customerId)
                .addItem(createOrderItem(sku, quantity)).build()).getId();
        orderIds.add(id);
        return id;
    }

    private OrderItemEntity createOrderItem(String sku, long quantity) {
        return OrderItemEntity.builder().sku(sku).unitPrice(new BigDecimal("100.00")).quantity(quantity).build();
    }

    private CreatePaymentAttemptRequest createPaymentRequest() {
        return new CreatePaymentAttemptRequest(PaymentProvider.STRIPE, UUID.randomUUID().toString());
    }

    private long readAvailableStock(UUID id) {
        return productRepository.findById(id).orElseThrow().getQuantity();
    }
}
