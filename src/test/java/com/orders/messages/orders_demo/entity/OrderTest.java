package com.orders.messages.orders_demo.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orders.messages.orders_demo.enums.OrderStatus;
import com.orders.messages.orders_demo.exceptions.InvalidOrderStateException;
import com.orders.messages.orders_demo.exceptions.OrderAlreadyCancelledException;

@ExtendWith(MockitoExtension.class)
public class OrderTest {
    private Order fakeOrder;

    @BeforeEach
    public void setup() {
        fakeOrder = new Order(new Customer("user_email", "user_name"), "mxn", new BigDecimal(12341.2131));
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

}
