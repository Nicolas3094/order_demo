package com.orders.messages.orders_demo.entity;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class OrderItemTest {

    @Test
    public void onInitialize_ShouldGetCorrectLineTotal() {
        BigDecimal expectedResult = new BigDecimal("1230.00");

        OrderItem orderItem = new OrderItem("sku", "descrpition", new BigDecimal("123.00"), 10L);

        assertEquals(expectedResult, orderItem.getLineTotal());
    }

    @Test
    public void changeUnitPrice_ShouldUpdateLineTotal() {
        BigDecimal expectedResult = new BigDecimal("2130.00");
        OrderItem orderItem = new OrderItem("sku", "descrpition", new BigDecimal("123.00"), 10L);

        orderItem.changeUnitPrice(new BigDecimal("213.00"));

        assertEquals(expectedResult, orderItem.getLineTotal());
    }

    @Test
    public void changeQuantity_ShouldUpdateLineTotal() {
        BigDecimal expectedResult = new BigDecimal("12300.00");
        OrderItem orderItem = new OrderItem("sku", "descrpition", new BigDecimal("123.00"), 10L);

        orderItem.changeQuantity(100L);

        assertEquals(expectedResult, orderItem.getLineTotal());
    }

}
