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

// No test-wide transaction: verify the API's own commit and rollback behavior.
class OrderInternalApiIntegrationTest extends PostgresContainer {
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderInternalApi orderInternalApi;

    private final List<UUID> orderIds = new ArrayList<>();
    private final List<UUID> customerIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            // Delete only this test's fixtures, including items through JPA cascade.
            orderIds.forEach(orderRepository::deleteById);
            orderRepository.flush();
            customerIds.forEach(customerRepository::deleteById);
            customerRepository.flush();
        });
    }

    @Test
    void shouldMarkAsPaidAndCommitWithoutCallerTransaction() {
        UUID id = persistOrder(OrderStatus.PENDING_PAYMENT);
        Long initialVersion = readOrder(id).getVersion();

        orderInternalApi.markAsPaid(id);

        OrderEntity persisted = readOrder(id);
        assertThat(persisted.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(persisted.getVersion()).isEqualTo(initialVersion + 1);
        assertThat(persisted.getAmountTotal()).isEqualByComparingTo("1250.50");
    }

    @Test
    void shouldRollBackPaymentWhenCallerTransactionFails() {
        UUID id = persistOrder(OrderStatus.PENDING_PAYMENT);
        Long initialVersion = readOrder(id).getVersion();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            orderInternalApi.markAsPaid(id);
            entityManager.flush(); // Force SQL before the simulated failure.
            throw new IllegalStateException("Simulated failure");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Simulated failure");

        OrderEntity persisted = readOrder(id);
        assertThat(persisted.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(persisted.getVersion()).isEqualTo(initialVersion);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PENDING_PAYMENT", mode = EnumSource.Mode.EXCLUDE)
    void shouldRejectPaymentAndPreserveTerminalState(OrderStatus state) {
        UUID id = persistOrder(state);
        Long initialVersion = readOrder(id).getVersion();
        Class<? extends RuntimeException> expected = switch (state) {
            case PAID -> OrderAlreadyPaidException.class;
            case CANCELLED -> OrderAlreadyCancelledException.class;
            case EXPIRED -> OrderAlreadyExpiredException.class;
            default -> InvalidOrderStateException.class;
        };

        assertThatThrownBy(() -> orderInternalApi.markAsPaid(id)).isInstanceOf(expected);
        OrderEntity persisted = readOrder(id);
        assertThat(persisted.getStatus()).isEqualTo(state);
        assertThat(persisted.getVersion()).isEqualTo(initialVersion);
    }

    @Test
    void shouldReturnPersistedOrder() {
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
    void shouldValidateExistenceRegardlessOfState(OrderStatus state) {
        UUID id = persistOrder(state);
        assertThatCode(() -> orderInternalApi.validateOrderExists(id)).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowPaymentAndReturnPersistedAmountForPendingOrder() {
        UUID id = persistOrder(OrderStatus.PENDING_PAYMENT);

        assertThatCode(() -> orderInternalApi.validateCanReceivePayment(id)).doesNotThrowAnyException();
        var data = orderInternalApi.getOrderDataForPayment(id);

        assertThat(data.orderId()).isEqualTo(id);
        assertThat(data.amountTotal()).isEqualByComparingTo("1250.50");
        assertThat(readOrder(id).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PENDING_PAYMENT", mode = EnumSource.Mode.EXCLUDE)
    void shouldRejectPaymentQueriesForTerminalOrders(OrderStatus state) {
        UUID id = persistOrder(state);

        assertThatThrownBy(() -> orderInternalApi.validateCanReceivePayment(id))
                .isInstanceOf(InvalidOrderStateException.class);
        assertThatThrownBy(() -> orderInternalApi.getOrderDataForPayment(id))
                .isInstanceOf(InvalidOrderStateException.class);
        assertThat(readOrder(id).getStatus()).isEqualTo(state);
    }

    @ParameterizedTest
    @MethodSource("operationsRequiringExistingOrder")
    void shouldRejectMissingOrder(Consumer<OrderInternalApi> operation) {
        assertThatThrownBy(() -> operation.accept(orderInternalApi))
                .isInstanceOf(OrderNotFoundException.class);
    }

    private static Stream<Consumer<OrderInternalApi>> operationsRequiringExistingOrder() {
        UUID missingId = UUID.randomUUID();
        return Stream.of(
                api -> api.markAsPaid(missingId),
                api -> api.getOrderById(missingId),
                api -> api.validateOrderExists(missingId),
                api -> api.validateCanReceivePayment(missingId),
                api -> api.getOrderDataForPayment(missingId));
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
