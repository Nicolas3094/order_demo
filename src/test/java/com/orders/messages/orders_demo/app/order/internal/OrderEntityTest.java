package com.orders.messages.orders_demo.app.order.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.order.internal.exceptions.InvalidOrderStateException;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderAlreadyCancelledException;
import com.orders.messages.orders_demo.app.order_item.internal.OrderItemEntity;

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

    @Test
    public void cancelOrder_WhenOrderHasPendingPaymentState_ShouldChangeStatusToCancelled() {
        fakeOrder.cancelOrder();

        assertEquals(OrderStatus.CANCELLED, fakeOrder.getStatus());
    }

    @Test
    public void markAsPaid_WhenOrderHasPendingPaymentState_ShouldChangeStatusToPaid() {
        fakeOrder.markAsPaid();

        assertEquals(OrderStatus.PAID, fakeOrder.getStatus());
    }

    @Test
    public void expire_WhenOrderHasPendingPaymentState_ShouldChangeStatusToExpired() {
        fakeOrder.expire();

        assertEquals(OrderStatus.EXPIRED, fakeOrder.getStatus());
    }

    @Test
    public void refund_WhenOrderHasPaidState_ShouldChangeStatusToRefunded() {
        fakeOrder.markAsPaid();

        fakeOrder.refund();

        assertEquals(OrderStatus.REFUNDED, fakeOrder.getStatus());
    }

    @Test
    public void refund_WhenOrderIsPending_ShouldThrowInvalidOrderStateExceptionWithMessage() {

        Exception result = assertThrows(InvalidOrderStateException.class, () -> fakeOrder.refund());

        assertEquals("Only paid orders can be refunded.", result.getMessage());
    }

    @Test
    public void cancelOrder_WhenOrderIsCancelled_ShouldThrowCancelledExceptionWithMessage() {
        fakeOrder.cancelOrder();

        Exception result = assertThrows(OrderAlreadyCancelledException.class, () -> fakeOrder.cancelOrder());

        assertEquals("Order is already cancelled.", result.getMessage());
    }

    @Test
    public void markAsPaid_WhenOrderIsCancelled_ShouldThrowCancelledExceptionWithMessage() {
        fakeOrder.cancelOrder();

        Exception result = assertThrows(OrderAlreadyCancelledException.class, () -> fakeOrder.markAsPaid());

        assertEquals("Order is already cancelled.", result.getMessage());
    }

    @Test
    public void expire_WhenOrderIsCancelled_ShouldThrowCancelledExceptionWithMessage() {
        fakeOrder.cancelOrder();

        Exception result = assertThrows(OrderAlreadyCancelledException.class, () -> fakeOrder.expire());

        assertEquals("Order is already cancelled.", result.getMessage());
    }

    @Test
    public void refund_WhenOrderIsCancelled_ShouldThrowCancelledExceptionWithMessage() {
        fakeOrder.cancelOrder();

        Exception result = assertThrows(OrderAlreadyCancelledException.class, () -> fakeOrder.refund());

        assertEquals("Order is already cancelled.", result.getMessage());
    }

    @Test
    public void cancelOrder_WhenOrderIsExpired_ShouldThrowInvalidOrderStateExceptionWithMessage() {
        fakeOrder.expire();

        Exception result = assertThrows(InvalidOrderStateException.class, () -> fakeOrder.cancelOrder());

        assertEquals("Expired orders cannot be modified.", result.getMessage());
    }

    @Test
    public void markAsPaid_WhenOrderIsExpired_ShouldThrowInvalidOrderStateExceptionWithMessage() {
        fakeOrder.expire();

        Exception result = assertThrows(InvalidOrderStateException.class, () -> fakeOrder.markAsPaid());

        assertEquals("Expired orders cannot be modified.", result.getMessage());
    }

    @Test
    public void expire_WhenOrderIsExpired_ShouldThrowInvalidOrderStateExceptionWithMessage() {
        fakeOrder.expire();

        Exception result = assertThrows(InvalidOrderStateException.class, () -> fakeOrder.expire());

        assertEquals("Expired orders cannot be modified.", result.getMessage());
    }

    @Test
    public void refund_WhenOrderIsExpired_ShouldThrowInvalidOrderStateExceptionWithMessage() {
        fakeOrder.expire();

        Exception result = assertThrows(InvalidOrderStateException.class, () -> fakeOrder.refund());

        assertEquals("Expired orders cannot be modified.", result.getMessage());
    }

    @Test
    public void cancelOrder_WhenOrderIsPaid_ShouldThrowInvalidOrderStateExceptionWithMessage() {
        fakeOrder.markAsPaid();

        Exception result = assertThrows(InvalidOrderStateException.class, () -> fakeOrder.cancelOrder());

        assertEquals("Paid orders cannot be modified.", result.getMessage());
    }

    @Test
    public void markAsPaid_WhenOrderIsPaid_ShouldThrowInvalidOrderStateExceptionWithMessage() {
        fakeOrder.markAsPaid();

        Exception result = assertThrows(InvalidOrderStateException.class, () -> fakeOrder.markAsPaid());

        assertEquals("Paid orders cannot be modified.", result.getMessage());
    }

    @Test
    public void expire_WhenOrderIsPaid_ShouldThrowInvalidOrderStateExceptionWithMessage() {
        fakeOrder.markAsPaid();

        Exception result = assertThrows(InvalidOrderStateException.class, () -> fakeOrder.expire());

        assertEquals("Paid orders cannot be modified.", result.getMessage());
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
    public void addItem_WhenMultipleItemsAreAdded_ShouldRecalculateAmountTotal() {
        OrderItemEntity item1 = OrderItemEntity.builder()
                .unitPrice(new BigDecimal("100.00"))
                .quantity(2L)
                .build();
        OrderItemEntity item2 = OrderItemEntity.builder()
                .unitPrice(new BigDecimal("50.00"))
                .quantity(4L)
                .build();

        fakeOrder.addItem(item1);
        fakeOrder.addItem(item2);
        assertEquals(new BigDecimal("400.00"), fakeOrder.getAmountTotal());
    }

    @Test
    public void removeItem_WhenOneItemIsRemoved_ShouldRecalculateAmountTotal() {
        OrderItemEntity item1 = OrderItemEntity.builder()
                .unitPrice(new BigDecimal("100.00"))
                .quantity(2L)
                .build();
        OrderItemEntity item2 = OrderItemEntity.builder()
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

}
