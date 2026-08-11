package com.orders.messages.orders_demo.app.order_item.internal;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.order.internal.OrderEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderStatus;
import com.orders.messages.orders_demo.app.order_item.internal.exceptions.InvalidOrderItemStateException;

public class OrderItemEntityTest {

    @Test
    public void onInitialize_ShouldGetCorrectLineTotal() {
        BigDecimal expectedResult = new BigDecimal("1230.00");
        OrderItemEntity orderItem = OrderItemEntity.builder()
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
        OrderEntity order = createOrderWithStatus(OrderStatus.PENDING_PAYMENT);
        OrderItemEntity orderItem = createOrderItemWithOrderStatus();
        order.addItem(orderItem);

        orderItem.changeUnitPrice(new BigDecimal("213.00"));

        assertEquals(expectedResult, orderItem.getLineTotal());
        assertEquals(new BigDecimal("213.00"), orderItem.getUnitPrice());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = Mode.EXCLUDE, names = { "PENDING_PAYMENT" })
    public void changeUnitPrice_WhenOrderIsNotPending_ShouldThrowInvalidOrderItemExcepion(OrderStatus orderStatus) {
        OrderEntity order = createOrderWithStatus(orderStatus);
        OrderItemEntity orderItem = createOrderItemWithOrderStatus();
        order.addItem(orderItem);

        InvalidOrderItemStateException result = assertThrows(InvalidOrderItemStateException.class,
                () -> orderItem.changeUnitPrice(new BigDecimal("213.00")));

        assertEquals("Only pending orders can modify items.", result.getMessage());
    }

    @Test
    public void changeQuantity_WhenOrderIsPending_ShouldUpdateLineTotal() {
        BigDecimal expectedResult = new BigDecimal("12300.00");
        OrderEntity order = createOrderWithStatus(OrderStatus.PENDING_PAYMENT);
        OrderItemEntity orderItem = createOrderItemWithOrderStatus();
        order.addItem(orderItem);

        orderItem.changeQuantity(100L);

        assertEquals(expectedResult, orderItem.getLineTotal());
        assertEquals(100L, orderItem.getQuantity());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = Mode.EXCLUDE, names = { "PENDING_PAYMENT" })
    public void changeQuantity_WhenOrderIsNotPending_ShouldThrowInvalidOrderItemExcepion(OrderStatus orderStatus) {
        OrderEntity order = createOrderWithStatus(orderStatus);
        OrderItemEntity orderItem = createOrderItemWithOrderStatus();
        order.addItem(orderItem);

        InvalidOrderItemStateException result = assertThrows(InvalidOrderItemStateException.class,
                () -> orderItem.changeQuantity(10L));

        assertEquals("Only pending orders can modify items.", result.getMessage());
    }

    @Test
    public void changeUnitPrice_WhenOrderIsNotAttached_ShouldThrowIllegalStateException() {
        OrderItemEntity orderItem = createOrderItemWithOrderStatus();

        IllegalStateException result = assertThrows(IllegalStateException.class,
                () -> orderItem.changeUnitPrice(new BigDecimal("10.00")));

        assertEquals("OrderItem is not attached to an Order.", result.getMessage());
    }

    @Test
    public void changeQuantity_WhenOrderIsNotAttached_ShouldThrowIllegalStateException() {
        OrderItemEntity orderItem = createOrderItemWithOrderStatus();

        IllegalStateException result = assertThrows(IllegalStateException.class,
                () -> orderItem.changeQuantity(2L));

        assertEquals("OrderItem is not attached to an Order.", result.getMessage());
    }

    @Test
    public void builder_WhenSkuIsBlank_ShouldThrowIllegalArgumentException() {

        IllegalArgumentException result = assertThrows(IllegalArgumentException.class,
                () -> OrderItemEntity.builder()
                        .sku("")
                        .build());

        assertEquals("Sku cannot be blank.", result.getMessage());
    }

    @Test
    public void builder_WhenSkuIsNull_ShouldThrowNullPointerException() {
        NullPointerException result = assertThrows(NullPointerException.class,
                () -> OrderItemEntity.builder()
                        .sku(null)
                        .build());

        assertEquals("SKU cannot be null.", result.getMessage());
    }

    @Test
    public void builder_WhenUnitPriceIsNull_ShouldThrowNullPointerException() {

        NullPointerException result = assertThrows(NullPointerException.class,
                () -> OrderItemEntity.builder()
                        .unitPrice(null)
                        .build());

        assertEquals("Price cannot be null.", result.getMessage());
    }

    @Test
    public void builder_WhenQuantityIsNull_ShouldThrowNullPointerException() {

        NullPointerException result = assertThrows(NullPointerException.class,
                () -> OrderItemEntity.builder()
                        .quantity(null)
                        .build());

        assertEquals("Quantity cannot be null.", result.getMessage());
    }

    @Test
    public void builder_WhenDescriptionIsNull_ShouldThrowNullPointerException() {

        NullPointerException result = assertThrows(NullPointerException.class,
                () -> OrderItemEntity.builder()
                        .description(null)
                        .build());

        assertEquals("Description cannot be null.", result.getMessage());
    }

    private static OrderEntity createOrderWithStatus(OrderStatus orderStatus) {
        return OrderEntity.builder()
                .customerId(UUID.randomUUID())
                .currency(Currency.MXN)
                .status(orderStatus)
                .build();
    }

    private static OrderItemEntity createOrderItemWithOrderStatus() {
        return OrderItemEntity.builder().sku("sku").description("description").unitPrice(new BigDecimal("123.00"))
                .quantity(10L).build();
    }

}
