package com.orders.messages.orders_demo.entity;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

import com.orders.messages.orders_demo.enums.Currency;
import com.orders.messages.orders_demo.enums.OrderStatus;
import com.orders.messages.orders_demo.exceptions.order_item.InvalidOrderItemStateException;

public class OrderItemTest {

    @Test
    public void onInitialize_ShouldGetCorrectLineTotal() {
        BigDecimal expectedResult = new BigDecimal("1230.00");
        OrderItem orderItem = OrderItem.builder()
                .sku("sku")
                .description("description")
                .unitPrice(new BigDecimal("123.00"))
                .quantity(10L)
                .build();

        assertEquals(expectedResult, orderItem.getLineTotal());
    }

    @Test
    public void changeUnitPrice_WhenOrderIsPending_ShouldUpdateLineTotal() {
        BigDecimal expectedResult = new BigDecimal("2130.00");
        Order order = createOrderWithStatus(OrderStatus.PENDING_PAYMENT);
        OrderItem orderItem = createOrderItemWithOrderStatus();
        order.addItem(orderItem);

        orderItem.changeUnitPrice(new BigDecimal("213.00"));

        assertEquals(expectedResult, orderItem.getLineTotal());
        assertEquals(new BigDecimal("213.00"), orderItem.getUnitPrice());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = Mode.EXCLUDE, names = { "PENDING_PAYMENT" })
    public void changeUnitPrice_WhenOrderIsNotPending_ShouldThrowInvalidOrderItemExcepion(OrderStatus orderStatus) {
        Order order = createOrderWithStatus(orderStatus);
        OrderItem orderItem = createOrderItemWithOrderStatus();
        order.addItem(orderItem);

        InvalidOrderItemStateException result = assertThrows(InvalidOrderItemStateException.class,
                () -> orderItem.changeUnitPrice(new BigDecimal("213.00")));

        assertEquals("Only pending orders can modify items.", result.getMessage());
    }

    @Test
    public void changeQuantity_WhenOrderIsPending_ShouldUpdateLineTotal() {
        BigDecimal expectedResult = new BigDecimal("12300.00");
        Order order = createOrderWithStatus(OrderStatus.PENDING_PAYMENT);
        OrderItem orderItem = createOrderItemWithOrderStatus();
        order.addItem(orderItem);

        orderItem.changeQuantity(100L);

        assertEquals(expectedResult, orderItem.getLineTotal());
        assertEquals(100L, orderItem.getQuantity());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = Mode.EXCLUDE, names = { "PENDING_PAYMENT" })
    public void changeQuantity_WhenOrderIsNotPending_ShouldThrowInvalidOrderItemExcepion(OrderStatus orderStatus) {
        Order order = createOrderWithStatus(orderStatus);
        OrderItem orderItem = createOrderItemWithOrderStatus();
        order.addItem(orderItem);

        InvalidOrderItemStateException result = assertThrows(InvalidOrderItemStateException.class,
                () -> orderItem.changeQuantity(10L));

        assertEquals("Only pending orders can modify items.", result.getMessage());
    }

    @Test
    public void changeUnitPrice_WhenOrderIsNotAttached_ShouldThrowIllegalStateException() {
        OrderItem orderItem = createOrderItemWithOrderStatus();

        IllegalStateException result = assertThrows(IllegalStateException.class,
                () -> orderItem.changeUnitPrice(new BigDecimal("10.00")));

        assertEquals("OrderItem is not attached to an Order.", result.getMessage());
    }

    @Test
    public void changeQuantity_WhenOrderIsNotAttached_ShouldThrowIllegalStateException() {
        OrderItem orderItem = createOrderItemWithOrderStatus();

        IllegalStateException result = assertThrows(IllegalStateException.class,
                () -> orderItem.changeQuantity(2L));

        assertEquals("OrderItem is not attached to an Order.", result.getMessage());
    }

    @Test
    public void builder_WhenSkuIsBlank_ShouldThrowIllegalArgumentException() {

        IllegalArgumentException result = assertThrows(IllegalArgumentException.class,
                () -> OrderItem.builder()
                        .sku("")
                        .build());

        assertEquals("Sku cannot be blank.", result.getMessage());
    }

    @Test
    public void builder_WhenSkuIsNull_ShouldThrowNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> OrderItem.builder()
                        .sku(null)
                        .build());
    }

    @Test
    public void builder_WhenUnitPriceIsNull_ShouldThrowNullPointerException() {

        assertThrows(NullPointerException.class,
                () -> OrderItem.builder()
                        .unitPrice(null)
                        .build());
    }

    @Test
    public void builder_WhenQuantityIsNull_ShouldThrowNullPointerException() {

        assertThrows(NullPointerException.class,
                () -> OrderItem.builder()
                        .quantity(null)
                        .build());
    }

    @Test
    public void builder_WhenDescriptionIsNull_ShouldThrowNullPointerException() {

        assertThrows(NullPointerException.class,
                () -> OrderItem.builder()
                        .description(null)
                        .build());
    }

    private static Order createOrderWithStatus(OrderStatus orderStatus) {
        return Order.builder()
                .customer(new Customer("email", "name"))
                .currency(Currency.MXN)
                .status(orderStatus)
                .build();
    }

    private static OrderItem createOrderItemWithOrderStatus() {
        return OrderItem.builder().sku("sku").description("description").unitPrice(new BigDecimal("123.00"))
                .quantity(10L).build();
    }

}
