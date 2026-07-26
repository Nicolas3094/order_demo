package com.orders.messages.orders_demo.entity;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.orders.messages.orders_demo.enums.OrderStatus;
import com.orders.messages.orders_demo.exceptions.order_item.InvalidOrderItemStateException;

public class OrderItemTest {

    @Test
    public void onInitialize_ShouldGetCorrectLineTotal() {
        BigDecimal expectedResult = new BigDecimal("1230.00");

        OrderItem orderItem = new OrderItem("sku", "descrpition", new BigDecimal("123.00"), 10L);

        assertEquals(expectedResult, orderItem.getLineTotal());
    }

    @Test
    public void changeUnitPrice_WhenOrderIsPending_ShouldUpdateLineTotal() {
        BigDecimal expectedResult = new BigDecimal("2130.00");
        OrderItem orderItem = createOrderItemWithOrderStatus(OrderStatus.PENDING_PAYMENT);

        orderItem.changeUnitPrice(new BigDecimal("213.00"));

        assertEquals(expectedResult, orderItem.getLineTotal());
        assertEquals(new BigDecimal("213.00"), orderItem.getUnitPrice());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = { "PAID", "CANCELLED", "REFUNDED", "EXPIRED" })
    public void changeUnitPrice_WhenOrderIsNotPending_ShouldThrowInvalidOrderItemExcepion(OrderStatus orderStatus) {
        OrderItem orderItem = createOrderItemWithOrderStatus(orderStatus);

        InvalidOrderItemStateException result = assertThrows(InvalidOrderItemStateException.class,
                () -> orderItem.changeUnitPrice(new BigDecimal("213.00")));

        assertEquals("Only pending orders can modify items", result.getMessage());
    }

    @Test
    public void changeQuantity_WhenOrderIsPending_ShouldUpdateLineTotal() {
        BigDecimal expectedResult = new BigDecimal("12300.00");
        OrderItem orderItem = createOrderItemWithOrderStatus(OrderStatus.PENDING_PAYMENT);

        orderItem.changeQuantity(100L);

        assertEquals(expectedResult, orderItem.getLineTotal());
        assertEquals(100L, orderItem.getQuantity());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = { "PAID", "CANCELLED", "REFUNDED", "EXPIRED" })
    public void changeQuantity_WhenOrderIsNotPending_ShouldThrowInvalidOrderItemExcepion(OrderStatus orderStatus) {
        OrderItem orderItem = createOrderItemWithOrderStatus(orderStatus);

        InvalidOrderItemStateException result = assertThrows(InvalidOrderItemStateException.class,
                () -> orderItem.changeQuantity(10L));

        assertEquals("Only pending orders can modify items", result.getMessage());
    }

    private static OrderItem createOrderItemWithOrderStatus(OrderStatus orderStatus) {
        return new OrderItem(new Order(new Customer("email", "name"), "MXN", new BigDecimal("123.00"), orderStatus),
                "sky", "description", new BigDecimal("123.00"), 10L);
    }

}
