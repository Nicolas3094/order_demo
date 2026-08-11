package com.orders.messages.orders_demo.app.order;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.order.api.OrderData;
import com.orders.messages.orders_demo.app.order.api.OrderResponse;
import com.orders.messages.orders_demo.app.order.internal.OrderEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderRepository;
import com.orders.messages.orders_demo.app.order.internal.OrderStatus;
import com.orders.messages.orders_demo.app.order.internal.exceptions.InvalidOrderStateException;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderNotFoundException;
import com.orders.messages.orders_demo.app.order_item.internal.OrderItemEntity;

@ExtendWith(MockitoExtension.class)
public class OrderInternalApiImplTest {

    private static final UUID ORDER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    private static final String ORDER_NOT_FOUND_MESSAGE = "Order with id 123e4567-e89b-12d3-a456-426614174000 not found.";

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderInternalApiImpl orderInternalApi;

    /*
     * 
     * getOrderById
     * 
     */

    @Test
    public void getOrderById_WhenOrderIsFound_ShouldReturnOrderResponse() {
        OrderEntity order = createOrder();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        OrderResponse result = orderInternalApi.getOrderById(ORDER_ID);

        assertEquals(order.getId(), result.id());
        assertEquals(order.getAmountTotal(), result.amountTotal());
        verify(orderRepository).findById(ORDER_ID);
    }

    @Test
    public void getOrderById_WhenOrderIsNotFound_ShouldThrowOrderNotFoundException() {
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.empty());

        OrderNotFoundException result = assertThrows(OrderNotFoundException.class,
                () -> orderInternalApi.getOrderById(ORDER_ID));

        assertEquals(ORDER_NOT_FOUND_MESSAGE, result.getMessage());
        verify(orderRepository).findById(ORDER_ID);
    }

    /*
     * 
     * markAsPaid
     * 
     */

    @Test
    public void markAsPaid_WhenOrderIsFound_ShouldMarkOrderAsPaidAndSave() {
        OrderEntity order = createOrder();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        orderInternalApi.markAsPaid(ORDER_ID);

        assertEquals(OrderStatus.PAID, order.getStatus());
        verify(orderRepository).findById(ORDER_ID);
        verify(orderRepository).save(order);
    }

    @Test
    public void markAsPaid_WhenOrderIsNotFound_ShouldThrowOrderNotFoundException() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());
        OrderNotFoundException result = assertThrows(OrderNotFoundException.class,
                () -> orderInternalApi.markAsPaid(ORDER_ID));

        assertEquals(ORDER_NOT_FOUND_MESSAGE, result.getMessage());
        verify(orderRepository, never()).save(any(OrderEntity.class));
    }

    /*
     * 
     * validateCanReceivePayment
     * 
     */

    @Test
    public void validateCanReceivePayment_WhenOrderIsPendingPayment_ShouldNotThrowException() {
        OrderEntity order = createOrder();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        orderInternalApi.validateCanReceivePayment(ORDER_ID);

        verify(orderRepository).findById(ORDER_ID);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = Mode.EXCLUDE, names = { "PENDING_PAYMENT" })
    public void validateCanReceivePayment_WhenOrderCannotAcceptPayments_ShouldThrowInvalidOrderStateException(
            OrderStatus status) {
        OrderEntity order = createOrderWithStatus(ORDER_ID, status);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        InvalidOrderStateException result = assertThrows(
                InvalidOrderStateException.class,
                () -> orderInternalApi.validateCanReceivePayment(ORDER_ID));

        assertEquals("This order cannot receive payment attempts.", result.getMessage());
    }

    @Test
    public void validateCanReceivePayment_WhenOrderIsNotFound_ShouldThrowOrderNotFoundException() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        OrderNotFoundException result = assertThrows(OrderNotFoundException.class,
                () -> orderInternalApi.validateCanReceivePayment(ORDER_ID));

        assertEquals(ORDER_NOT_FOUND_MESSAGE, result.getMessage());
    }

    /*
     * 
     * getOrderDataForPayment
     * 
     */

    @Test
    public void getOrderDataForPayment_WhenOrderCanReceivePayment_ShouldReturnOrderData() {
        OrderEntity order = createOrder();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        OrderData result = orderInternalApi.getOrderDataForPayment(ORDER_ID);

        assertEquals(ORDER_ID, result.orderId());
        assertEquals(order.getAmountTotal(), result.amountTotal());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = Mode.EXCLUDE, names = { "PENDING_PAYMENT" })
    public void getOrderDataForPayment_WhenOrderCannotReceivePayment_ShouldThrowInvalidOrderStateException(
            OrderStatus status) {
        OrderEntity order = createOrderWithStatus(ORDER_ID, status);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        InvalidOrderStateException result = assertThrows(InvalidOrderStateException.class,
                () -> orderInternalApi.getOrderDataForPayment(ORDER_ID));

        assertEquals("This order cannot receive payment attempts.", result.getMessage());
    }

    @Test
    public void getOrderDataForPayment_WhenOrderIsNotFound_ShouldThrowOrderNotFoundException() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        OrderNotFoundException result = assertThrows(OrderNotFoundException.class,
                () -> orderInternalApi.getOrderDataForPayment(ORDER_ID));

        assertEquals(ORDER_NOT_FOUND_MESSAGE, result.getMessage());
    }

    /*
     * 
     * validateOrderExists
     * 
     */

    @Test
    public void validateOrderExists_WhenOrderExists_ShouldNotThrowException() {
        when(orderRepository.existsById(ORDER_ID)).thenReturn(true);

        orderInternalApi.validateOrderExists(ORDER_ID);

        verify(orderRepository).existsById(ORDER_ID);
    }

    @Test
    public void validateOrderExists_WhenOrderDoesNotExist_ShouldThrowOrderNotFoundException() {
        when(orderRepository.existsById(ORDER_ID)).thenReturn(false);

        OrderNotFoundException result = assertThrows(OrderNotFoundException.class,
                () -> orderInternalApi.validateOrderExists(ORDER_ID));

        assertEquals(ORDER_NOT_FOUND_MESSAGE, result.getMessage());
        verify(orderRepository).existsById(ORDER_ID);
    }

    private static OrderEntity createOrder() {
        return OrderEntity.builder()
                .id(ORDER_ID)
                .customer(new CustomerEntity("email", "name"))
                .currency(Currency.MXN)
                .addItem(
                        OrderItemEntity.builder()
                                .sku("SKU")
                                .description("Item")
                                .unitPrice(new BigDecimal("250.00"))
                                .quantity(1L)
                                .build())
                .build();
    }

    private static OrderEntity createOrderWithStatus(UUID orderId, OrderStatus status) {
        return OrderEntity.builder()
                .id(orderId)
                .customer(new CustomerEntity("email", "name"))
                .currency(Currency.MXN)
                .status(status)
                .build();
    }
}