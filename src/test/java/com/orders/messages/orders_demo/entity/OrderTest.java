package com.orders.messages.orders_demo.entity;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orders.messages.orders_demo.enums.Currency;
import com.orders.messages.orders_demo.enums.OrderStatus;
import com.orders.messages.orders_demo.exceptions.orders.InvalidOrderStateException;
import com.orders.messages.orders_demo.exceptions.orders.OrderAlreadyCancelledException;

@ExtendWith(MockitoExtension.class)
public class OrderTest {
    private Order fakeOrder;

    @BeforeEach
    public void setup() {
        fakeOrder = Order.builder()
                .customer(new Customer("user_email", "user_name"))
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
        OrderItem item = OrderItem.builder()
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
        OrderItem item = OrderItem.builder()
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
        OrderItem item1 = OrderItem.builder()
                .unitPrice(new BigDecimal("100.00"))
                .quantity(2L)
                .build();
        OrderItem item2 = OrderItem.builder()
                .unitPrice(new BigDecimal("50.00"))
                .quantity(4L)
                .build();

        fakeOrder.addItem(item1);
        fakeOrder.addItem(item2);
        assertEquals(new BigDecimal("400.00"), fakeOrder.getAmountTotal());
    }

    @Test
    public void removeItem_WhenOneItemIsRemoved_ShouldRecalculateAmountTotal() {
        OrderItem item1 = OrderItem.builder()
                .unitPrice(new BigDecimal("100.00"))
                .quantity(2L)
                .build();
        OrderItem item2 = OrderItem.builder()
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
        fakeOrder.addItem(OrderItem.builder().build());

        List<OrderItem> items = fakeOrder.getItems();

        assertThrows(UnsupportedOperationException.class,
                () -> items.clear());
    }

    @Test
    public void builder_WhenCustomerIsNull_ShouldThrowIllegalStateException() {

        IllegalStateException result = assertThrows(
                IllegalStateException.class,
                () -> Order.builder()
                        .currency(Currency.MXN)
                        .build());

        assertEquals("Customer is required.", result.getMessage());
    }

    @Test
    public void builder_WhenCurrencyIsNull_ShouldThrowIllegalStateException() {

        NullPointerException result = assertThrows(
                NullPointerException.class,
                () -> Order.builder()
                        .customer(new Customer("email", "name"))
                        .currency(null)
                        .build());

        assertEquals("Currency is required.", result.getMessage());
    }

    @Test
    public void validateCurrency_WhenProductCurrencyMatchesOrderCurrency_ShouldReturnTrue() {
        Product product = Product.builder()
                .sku("SKU")
                .name("Product")
                .price(new BigDecimal("100.00"))
                .currency(Currency.MXN)
                .build();

        boolean result = fakeOrder.validateCurrency(product);

        assertEquals(true, result);
    }

    @Test
    public void validateCurrency_WhenProductCurrencyDoesNotMatchOrderCurrency_ShouldThrowInvalidProductException() {
        Product product = Product.builder()
                .sku("SKU")
                .name("Product")
                .price(new BigDecimal("100.00"))
                .currency(Currency.USD)
                .build();

        InvalidOrderStateException result = assertThrows(InvalidOrderStateException.class,
                () -> fakeOrder.validateCurrency(product));

        assertEquals("Product currency USD does not match order currency MXN.", result.getMessage());
    }

}
