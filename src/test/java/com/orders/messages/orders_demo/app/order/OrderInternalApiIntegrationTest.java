package com.orders.messages.orders_demo.app.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.orders.messages.orders_demo.app.PostgresContainer;
import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.order.api.OrderInternalApi;
import com.orders.messages.orders_demo.app.order.internal.OrderEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderItemEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderRepository;
import com.orders.messages.orders_demo.app.order.internal.OrderStatus;
import com.orders.messages.orders_demo.app.order.internal.exceptions.InvalidOrderStateException;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderAlreadyCancelledException;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderAlreadyExpiredException;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderAlreadyPaidException;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderNotFoundException;

public class OrderInternalApiIntegrationTest extends PostgresContainer {
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderInternalApi orderInternalApi;

    private final List<UUID> orderIds = new ArrayList<>();
    private final List<UUID> customerIds = new ArrayList<>();

    @AfterEach
    public void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            orderIds.forEach(orderRepository::deleteById);
            orderRepository.flush();
            customerIds.forEach(customerRepository::deleteById);
            customerRepository.flush();
        });
    }

    @Test
    public void shouldMarkAsPaidAndCommitWithoutCallerTransaction() {
        UUID id = persistOrder(OrderStatus.PAYMENT_IN_PROGRESS);
        Long initialVersion = readOrder(id).getVersion();

        orderInternalApi.markAsPaid(id);

        OrderEntity persisted = readOrder(id);
        assertThat(persisted.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(persisted.getVersion()).isEqualTo(initialVersion + 1);
        assertThat(persisted.getAmountTotal()).isEqualByComparingTo("1250.50");
    }

    @Test
    public void shouldRollBackPaymentWhenCallerTransactionFails() {
        UUID id = persistOrder(OrderStatus.PAYMENT_IN_PROGRESS);
        Long initialVersion = readOrder(id).getVersion();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            orderInternalApi.markAsPaid(id);
            entityManager.flush(); // Force SQL before the simulated failure.
            throw new IllegalStateException("Simulated failure");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Simulated failure");

        OrderEntity persisted = readOrder(id);
        assertThat(persisted.getStatus()).isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
        assertThat(persisted.getVersion()).isEqualTo(initialVersion);
    }

    @Test
    public void shouldMarkAsPaymentInProgressAndCommitWithoutCallerTransaction() {
        UUID id = persistOrder(OrderStatus.PENDING_PAYMENT);
        Long initialVersion = readOrder(id).getVersion();

        orderInternalApi.markAsPaymentInProgress(id);

        OrderEntity persisted = readOrder(id);
        assertThat(persisted.getStatus()).isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
        assertThat(persisted.getVersion()).isEqualTo(initialVersion + 1);
    }

    @Test
    public void shouldRollBackPaymentInProgressWhenCallerTransactionFails() {
        UUID id = persistOrder(OrderStatus.PENDING_PAYMENT);
        Long initialVersion = readOrder(id).getVersion();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            orderInternalApi.markAsPaymentInProgress(id);
            entityManager.flush(); // Force SQL before the simulated failure.
            throw new IllegalStateException("Simulated failure");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Simulated failure");

        OrderEntity persisted = readOrder(id);
        assertThat(persisted.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(persisted.getVersion()).isEqualTo(initialVersion);
    }

    @ParameterizedTest
    @MethodSource("rejectPaymentStates")
    public void shouldRejectSetPaymentInProgressAndPreserveTerminalState(
            OrderStatus state, Class<? extends RuntimeException> expectedException) {
        UUID id = persistOrder(state);
        Long initialVersion = readOrder(id).getVersion();

        assertThatThrownBy(() -> orderInternalApi.markAsPaymentInProgress(id)).isInstanceOf(expectedException);

        OrderEntity persisted = readOrder(id);
        assertThat(persisted.getStatus()).isEqualTo(state);
        assertThat(persisted.getVersion()).isEqualTo(initialVersion);
    }

    @ParameterizedTest
    @MethodSource("rejectMarkAsPaidStates")
    public void shouldRejectPaymentAndPreserveTerminalState(
            OrderStatus state, Class<? extends RuntimeException> expectedException) {
        UUID id = persistOrder(state);
        Long initialVersion = readOrder(id).getVersion();

        assertThatThrownBy(() -> orderInternalApi.markAsPaid(id)).isInstanceOf(expectedException);

        OrderEntity persisted = readOrder(id);
        assertThat(persisted.getStatus()).isEqualTo(state);
        assertThat(persisted.getVersion()).isEqualTo(initialVersion);
    }

    @Test
    public void shouldReturnPersistedOrder() {
        UUID id = persistOrder(OrderStatus.PENDING_PAYMENT);
        OrderEntity persisted = readOrder(id);

        var response = orderInternalApi.getOrderById(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.customerId()).isEqualTo(persisted.getCustomerId());
        assertThat(response.currency()).isEqualTo(Currency.MXN);
        assertThat(response.amountTotal()).isEqualByComparingTo("1250.50");
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(response.createdAt()).isEqualTo(persisted.getCreatedAt());
        assertThat(response.updatedAt()).isEqualTo(persisted.getUpdatedAt());
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    public void shouldValidateExistenceRegardlessOfState(OrderStatus state) {
        UUID id = persistOrder(state);
        assertThatCode(() -> orderInternalApi.validateOrderExists(id)).doesNotThrowAnyException();
    }

    @Test
    public void shouldAllowProcessingForOrderWithPaymentInProgress() {
        UUID id = persistOrder(OrderStatus.PAYMENT_IN_PROGRESS);

        assertThatCode(() -> orderInternalApi.validateCanReceivePayment(id)).doesNotThrowAnyException();

        assertThat(readOrder(id).getStatus()).isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
    }

    @Test
    public void shouldReturnPaymentDataForPendingOrder() {
        UUID id = persistOrder(OrderStatus.PENDING_PAYMENT);

        var data = orderInternalApi.getOrderDataForPayment(id);

        assertThat(data.orderId()).isEqualTo(id);
        assertThat(data.amountTotal()).isEqualByComparingTo("1250.50");
        assertThat(readOrder(id).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PENDING_PAYMENT", mode = EnumSource.Mode.EXCLUDE)
    public void shouldRejectPaymentQueriesForTerminalOrders(OrderStatus state) {
        UUID id = persistOrder(state);

        assertThatThrownBy(() -> orderInternalApi.getOrderDataForPayment(id))
                .isInstanceOf(InvalidOrderStateException.class);
        assertThat(readOrder(id).getStatus()).isEqualTo(state);
    }

    @ParameterizedTest
    @MethodSource("operationsRequiringExistingOrder")
    public void shouldRejectMissingOrder(Consumer<OrderInternalApi> operation) {

        assertThatThrownBy(() -> operation.accept(orderInternalApi)).isInstanceOf(OrderNotFoundException.class);

    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PAYMENT_IN_PROGRESS", mode = EnumSource.Mode.EXCLUDE)
    public void shouldRejectProcessingUnlessOrderHasPaymentInProgress(OrderStatus state) {
        UUID id = persistOrder(state);

        assertThatThrownBy(() -> orderInternalApi.validateCanReceivePayment(id))
                .isInstanceOf(InvalidOrderStateException.class);

        assertThat(readOrder(id).getStatus()).isEqualTo(state);
    }

    private static Stream<Arguments> rejectMarkAsPaidStates() {
        return Stream.of(
                Arguments.of(OrderStatus.PENDING_PAYMENT, InvalidOrderStateException.class),
                Arguments.of(OrderStatus.PAID, OrderAlreadyPaidException.class),
                Arguments.of(OrderStatus.CANCELLED, OrderAlreadyCancelledException.class),
                Arguments.of(OrderStatus.EXPIRED, OrderAlreadyExpiredException.class),
                Arguments.of(OrderStatus.REFUNDED, InvalidOrderStateException.class));
    }

    @Test
    public void shouldMarkAsPendingAndCommitWithoutCallerTransaction() {
        UUID id = persistOrder(OrderStatus.PAYMENT_IN_PROGRESS);
        Long initialVersion = readOrder(id).getVersion();

        orderInternalApi.markAsPending(id);

        OrderEntity persisted = readOrder(id);
        assertThat(persisted.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(persisted.getVersion()).isEqualTo(initialVersion + 1);
    }

    @Test
    public void shouldRollBackPendingTransitionWhenCallerTransactionFails() {
        UUID id = persistOrder(OrderStatus.PAYMENT_IN_PROGRESS);
        Long initialVersion = readOrder(id).getVersion();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            orderInternalApi.markAsPending(id);
            entityManager.flush();
            throw new IllegalStateException("Simulated failure");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Simulated failure");

        OrderEntity persisted = readOrder(id);
        assertThat(persisted.getStatus()).isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
        assertThat(persisted.getVersion()).isEqualTo(initialVersion);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PAYMENT_IN_PROGRESS", mode = EnumSource.Mode.EXCLUDE)
    public void shouldRejectPendingTransitionFromOtherStates(OrderStatus state) {
        UUID id = persistOrder(state);
        Long initialVersion = readOrder(id).getVersion();

        assertThatThrownBy(() -> orderInternalApi.markAsPending(id))
                .isInstanceOf(InvalidOrderStateException.class);

        OrderEntity persisted = readOrder(id);
        assertThat(persisted.getStatus()).isEqualTo(state);
        assertThat(persisted.getVersion()).isEqualTo(initialVersion);
    }

    private static Stream<Consumer<OrderInternalApi>> operationsRequiringExistingOrder() {
        UUID missingId = UUID.randomUUID();
        return Stream.of(
                api -> api.markAsPaymentInProgress(missingId),
                api -> api.markAsPending(missingId),
                api -> api.getAllOrderItemsFromOrder(missingId),
                api -> api.markAsPaid(missingId),
                api -> api.getOrderById(missingId),
                api -> api.validateOrderExists(missingId),
                api -> api.validateCanReceivePayment(missingId),
                api -> api.getOrderDataForPayment(missingId));
    }

    private static Stream<Arguments> rejectPaymentStates() {
        return Stream.of(
                Arguments.of(OrderStatus.PAID, OrderAlreadyPaidException.class),
                Arguments.of(OrderStatus.CANCELLED, OrderAlreadyCancelledException.class),
                Arguments.of(OrderStatus.EXPIRED, OrderAlreadyExpiredException.class),
                Arguments.of(OrderStatus.PAYMENT_IN_PROGRESS, InvalidOrderStateException.class),
                Arguments.of(OrderStatus.REFUNDED, InvalidOrderStateException.class));
    }

    @Test
    void shouldReturnPersistedOrderItemsInsideCallerTransactionWithoutChangingOrder() {
        UUID orderId = persistOrder(OrderStatus.PENDING_PAYMENT);
        Long initialVersion = readOrder(orderId).getVersion();

        var orderItemResponses = transactionTemplate.execute(status -> orderInternalApi.getAllOrderItemsFromOrder(orderId));

        assertThat(orderItemResponses).hasSize(1);
        var orderItemResponse = orderItemResponses.get(0);
        assertThat(orderItemResponse.id()).isNotNull();
        assertThat(orderItemResponse.orderId()).isEqualTo(orderId);
        assertThat(orderItemResponse.quantity()).isEqualTo(1L);
        assertThat(orderItemResponse.unitPrice()).isEqualByComparingTo("1250.50");
        assertThat(orderItemResponse.lineTotal()).isEqualByComparingTo("1250.50");
        assertThat(readOrder(orderId).getVersion()).isEqualTo(initialVersion);
    }

    @Test
    void shouldReturnPersistedOrderItemsWithoutCallerTransaction() {
        UUID orderId = persistOrder(OrderStatus.PENDING_PAYMENT);

        var orderItemResponses = orderInternalApi.getAllOrderItemsFromOrder(orderId);

        assertThat(orderItemResponses).hasSize(1);
        assertThat(orderItemResponses.get(0).orderId()).isEqualTo(orderId);
    }

    private UUID persistOrder(OrderStatus state) {
        return transactionTemplate.execute(status -> {

            CustomerEntity customer = customerRepository.saveAndFlush(
                    new CustomerEntity(UUID.randomUUID() + "@example.com", "Test Customer"));

            customerIds.add(customer.getId());

            OrderEntity order = OrderEntity.builder()
                    .customerId(customer.getId())
                    .currency(Currency.MXN)
                    .status(state)
                    .addItem(OrderItemEntity.builder()
                            .description("order item 1")
                            .unitPrice(new BigDecimal("1250.50"))
                            .build())
                    .build();

            OrderEntity saved = orderRepository.saveAndFlush(order);

            orderIds.add(saved.getId());

            return saved.getId();
        });
    }

    private OrderEntity readOrder(UUID id) {
        // Read in a fresh persistence context after the previous transaction ended.
        return transactionTemplate.execute(status -> {

            entityManager.clear();

            return orderRepository.findById(id).orElseThrow();
        });
    }

}
