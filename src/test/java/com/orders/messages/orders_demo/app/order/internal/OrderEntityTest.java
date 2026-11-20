package com.orders.messages.orders_demo.app.order.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.order.internal.exceptions.InvalidOrderStateException;

@ExtendWith(MockitoExtension.class)
public class OrderEntityTest {
    private OrderEntity fakeOrder;

    @BeforeEach
    public void setup() {
        fakeOrder = OrderEntity.builder()
                .customerId(UUID.randomUUID())
                .currency(Currency.MXN)
                .build();
    }

    @Test
    public void whenOrderInstantiated_ShouldHavePendingPaymentState() {
        assertEquals(OrderStatus.PENDING_PAYMENT, fakeOrder.getStatus());

    }

    @ParameterizedTest
    @MethodSource("orderTransitionsFromPending")
    public void stateChange_WhenOrderIsPending_ShouldChangeToExpectedStatus(
            Consumer<OrderEntity> operation, OrderStatus expectedStatus) {
        operation.accept(fakeOrder);

        assertEquals(expectedStatus, fakeOrder.getStatus());
    }

    @ParameterizedTest
    @MethodSource("invalidStateTransitions")
    public void stateChange_WhenCurrentStateDoesNotAllowOperation_ShouldRejectAndPreserveStatus(
            Consumer<OrderEntity> mainOperation, Consumer<OrderEntity> prevOperation, String expectedMessage) {
        prevOperation.accept(fakeOrder);
        var initialStatus = fakeOrder.getStatus();

        Exception result = assertThrows(InvalidOrderStateException.class, () -> mainOperation.accept(fakeOrder));

        assertEquals(expectedMessage, result.getMessage());
        assertEquals(initialStatus, fakeOrder.getStatus());
    }

    @Test
    public void refund_WhenOrderHasPaidState_ShouldChangeStatusToRefunded() {
        fakeOrder.markAsPaid();

        fakeOrder.refund();

        assertEquals(OrderStatus.REFUNDED, fakeOrder.getStatus());
    }

    @ParameterizedTest
    @MethodSource("refundTransitionsError")
    public void refund_WhenOrderIsNotFromPaid_ShouldThrowInvalidOrderStateExceptionWithMessage(
            Consumer<OrderEntity> operation, String expectedMessage) {
        operation.accept(fakeOrder);
        var initialStatus = fakeOrder.getStatus();

        Exception result = assertThrows(InvalidOrderStateException.class, () -> fakeOrder.refund());

        assertEquals(expectedMessage, result.getMessage());
        assertEquals(initialStatus, fakeOrder.getStatus());
    }

    @Test
    public void addItem_WhenItemIsAdded_ShouldAttachOrderAndRecalculateAmountTotal() {
        OrderItemEntity item = OrderItemEntity.builder()
                .sku("SKU")
                .description("Item")
                .unitPrice(new BigDecimal("100.00"))
                .quantity(2L)
                .build();

        fakeOrder.addItem(item);

        assertEquals(1, fakeOrder.getItems().size());
        assertEquals(fakeOrder, item.getOrder());
        assertEquals(new BigDecimal("200.00"), fakeOrder.getAmountTotal());
    }

    @Test
    public void addItem_WhenMultipleItemsAreAdded_ShouldRecalculateAmountTotal() {
        OrderItemEntity item1 = OrderItemEntity.builder()
                .sku("SKU-001")
                .unitPrice(new BigDecimal("100.00"))
                .quantity(2L)
                .build();
        OrderItemEntity item2 = OrderItemEntity.builder()
                .sku("SKU-002")
                .unitPrice(new BigDecimal("50.00"))
                .quantity(4L)
                .build();

        fakeOrder.addItem(item1);
        fakeOrder.addItem(item2);
        assertEquals(new BigDecimal("400.00"), fakeOrder.getAmountTotal());
    }

    @Test
    public void addItem_WhenSkuAlreadyExists_ShouldIncreaseQuantityAndKeepExistingItem() {
        OrderItemEntity existingItem = OrderItemEntity.builder()
                .id(UUID.randomUUID())
                .sku("SKU-001")
                .unitPrice(new BigDecimal("100.00"))
                .quantity(2L)
                .build();
        OrderItemEntity newItem = OrderItemEntity.builder()
                .sku("SKU-001")
                .unitPrice(new BigDecimal("100.00"))
                .quantity(3L)
                .build();
        fakeOrder.addItem(existingItem);

        fakeOrder.addItem(newItem);

        assertEquals(1, fakeOrder.getItems().size());
        assertSame(existingItem, fakeOrder.getItems().get(0));
        assertSame(fakeOrder, existingItem.getOrder());
        assertEquals(5L, existingItem.getQuantity());
        assertEquals(new BigDecimal("500.00"), existingItem.getLineTotal());
        assertEquals(new BigDecimal("500.00"), fakeOrder.getAmountTotal());
    }

    @Test
    public void removeItem_WhenItemExists_ShouldDetachOrderAndRecalculateAmountTotal() {
        OrderItemEntity item = OrderItemEntity.builder()
                .sku("SKU")
                .description("Item")
                .unitPrice(new BigDecimal("100.00"))
                .quantity(2L)
                .build();
        fakeOrder.addItem(item);

        fakeOrder.removeItem(item);

        assertEquals(0, fakeOrder.getItems().size());
        assertEquals(BigDecimal.ZERO, fakeOrder.getAmountTotal());
    }

    @Test
    public void removeItem_WhenOneItemIsRemoved_ShouldRecalculateAmountTotal() {
        OrderItemEntity item1 = OrderItemEntity.builder()
                .sku("SKU-001")
                .unitPrice(new BigDecimal("100.00"))
                .quantity(2L)
                .build();
        OrderItemEntity item2 = OrderItemEntity.builder()
                .sku("SKU-002")
                .unitPrice(new BigDecimal("50.00"))
                .quantity(4L)
                .build();
        fakeOrder.addItem(item1);
        fakeOrder.addItem(item2);

        fakeOrder.removeItem(item1);

        assertEquals(new BigDecimal("200.00"), fakeOrder.getAmountTotal());
    }

    @Test
    public void getItems_ShouldNotAllowExternalModification() {
        fakeOrder.addItem(OrderItemEntity.builder().build());
        List<OrderItemEntity> items = fakeOrder.getItems();

        UnsupportedOperationException result = assertThrows(UnsupportedOperationException.class,
                () -> items.clear());

        assertNotNull(result);
    }

    @Test
    public void builder_WhenCustomerIsNull_ShouldThrowIllegalStateException() {

        IllegalStateException result = assertThrows(
                IllegalStateException.class,
                () -> OrderEntity.builder()
                        .currency(Currency.MXN)
                        .build());

        assertEquals("Customer ID is required.", result.getMessage());
    }

    @Test
    public void builder_WhenCurrencyIsNull_ShouldThrowIllegalStateException() {

        NullPointerException result = assertThrows(
                NullPointerException.class,
                () -> OrderEntity.builder()
                        .customerId(UUID.randomUUID())
                        .currency(null)
                        .build());

        assertEquals("Currency is required.", result.getMessage());
    }

    @Test
    public void validateCurrency_WhenProductCurrencyMatchesOrderCurrency_ShouldReturnTrue() {

        boolean result = fakeOrder.validateCurrency(Currency.MXN);

        assertEquals(true, result);
    }

    @Test
    public void validateCurrency_WhenProductCurrencyDoesNotMatchOrderCurrency_ShouldThrowInvalidProductException() {

        InvalidOrderStateException result = assertThrows(InvalidOrderStateException.class,
                () -> fakeOrder.validateCurrency(Currency.USD));

        assertEquals("Product currency USD does not match order currency MXN.", result.getMessage());
    }

    private static Stream<Arguments> orderTransitionsFromPending() {
        return Stream.of(
                Arguments.of(
                        (Consumer<OrderEntity>) OrderEntity::cancelOrder,
                        OrderStatus.CANCELLED),
                Arguments.of(
                        (Consumer<OrderEntity>) OrderEntity::markAsPaid,
                        OrderStatus.PAID),
                Arguments.of(
                        (Consumer<OrderEntity>) OrderEntity::expire,
                        OrderStatus.EXPIRED),
                Arguments.of(
                        (Consumer<OrderEntity>) OrderEntity::paymentInProgress,
                        OrderStatus.PAYMENT_IN_PROGRESS));
    }

    private static Stream<Arguments> invalidStateTransitions() {
        List<Map.Entry<Consumer<OrderEntity>, String>> cases = List.of(
                Map.entry(OrderEntity::cancelOrder, "Order is already cancelled."),
                Map.entry(OrderEntity::markAsPaid, "Paid orders cannot be modified."),
                Map.entry(OrderEntity::expire, "Expired orders cannot be modified."),
                Map.entry(OrderEntity::paymentInProgress, "Order is under a payment process."));

        List<Consumer<OrderEntity>> operationsToReject = List.of(OrderEntity::expire, OrderEntity::markAsPaid,
                OrderEntity::cancelOrder, OrderEntity::paymentInProgress);

        return operationsToReject.stream()
                .flatMap(mainOperation -> cases.stream()
                        .map(mapEntity -> Arguments.of(
                                mainOperation,
                                mapEntity.getKey(),
                                mapEntity.getValue())));
    }

    private static Stream<Arguments> refundTransitionsError() {
        return Stream.of(
                Arguments.of(
                        (Consumer<OrderEntity>) OrderEntity::cancelOrder,
                        "Order is already cancelled."),
                Arguments.of(
                        (Consumer<OrderEntity>) order -> {
                        },
                        "Only paid orders can be refunded."),
                Arguments.of((Consumer<OrderEntity>) OrderEntity::expire,
                        "Expired orders cannot be modified."),
                Arguments.of((Consumer<OrderEntity>) OrderEntity::paymentInProgress,
                        "Order is under a payment process."));
    }

}
