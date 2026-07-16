package com.orders.messages.orders_demo.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orders.messages.orders_demo.dtos.request.CreateOrderRequest;
import com.orders.messages.orders_demo.entity.Customer;
import com.orders.messages.orders_demo.entity.Order;
import com.orders.messages.orders_demo.enums.CustomerStatus;
import com.orders.messages.orders_demo.enums.OrderStatus;
import com.orders.messages.orders_demo.exceptions.CustomerNotFoundException;
import com.orders.messages.orders_demo.exceptions.OrderNotFoundException;
import com.orders.messages.orders_demo.mappers.OrderMapper;
import com.orders.messages.orders_demo.repositories.CustomerRepository;
import com.orders.messages.orders_demo.repositories.OrderRepository;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    private Order fakeOrder;
    private UUID orderId;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    public void setup() {
        fakeOrder = new Order(new Customer("email", "name"), "mxn", new BigDecimal(12341.2131));
        orderId = fakeOrder.getId();
    }

    @Test
    public void getOrder_WhenOrderExists_ReturnsOrder() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));

        Order result = orderService.getOrder(orderId);

        assertEquals(orderId, result.getId());

    }

    @Test
    public void getOrder_WhenOrderDoesNotExist_ThrowsOrderNotFoundException() {
        assertThrows(OrderNotFoundException.class, () -> orderService.getOrder(orderId));
    }

    @Test
    public void createOrder_WhenCustomerExists_ShouldSaveOrder() {
        UUID customerId = UUID.randomUUID();
        Customer customer = new Customer(customerId, "email", "name", CustomerStatus.ACTIVE, Instant.now());
        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                .setCustomerId(customerId)
                .setCurrency("currency")
                .setAmountTotal(new BigDecimal(123))
                .build();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.createOrder(orderRequest);

        assertEquals(customer, result.getCustomer());
        assertEquals(orderRequest.currency(), result.getCurrency());
        assertEquals(orderRequest.amountTotal(), result.getAmountTotal());
        assertEquals(OrderStatus.PENDING_PAYMENT, result.getStatus());
    }

    @Test
    public void createOrder_WhenCustomerNotExists_ShouldThrowCustomerNotFoundException() {
        UUID customerId = UUID.randomUUID();
        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                .setCustomerId(customerId)
                .setCurrency("currency")
                .setAmountTotal(new BigDecimal(123))
                .build();

        Exception result = assertThrows(CustomerNotFoundException.class, () -> orderService.createOrder(orderRequest));

        assertEquals("Order could not be found.", result.getMessage());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    public void cancelOrder_WhenOrderExists_ShouldCancelOrder() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));

        orderService.cancelOrder(orderId);

        assertEquals(OrderStatus.CANCELLED, fakeOrder.getStatus());
        verify(orderRepository).save(fakeOrder);
    }

    @Test
    public void cancelOrder_WhenOrderDoesNotExist_ShouldThrowOrderNotFoundException() {
        assertThrows(OrderNotFoundException.class, () -> orderService.cancelOrder(orderId));
    }

    @Test
    public void payOrder_WhenOrderExists_ShouldMarkOrderAsPaid() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));

        orderService.payOrder(orderId);

        assertEquals(OrderStatus.PAID, fakeOrder.getStatus());
        verify(orderRepository).save(fakeOrder);
    }

    @Test
    public void payOrder_WhenOrderDoesNotExist_ShouldThrowOrderNotFoundException() {
        assertThrows(OrderNotFoundException.class, () -> orderService.payOrder(orderId));
    }

    @Test
    public void expireOrder_WhenOrderExists_ShouldExpireOrder() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));

        orderService.expireOrder(orderId);

        assertEquals(OrderStatus.EXPIRED, fakeOrder.getStatus());
        verify(orderRepository).save(fakeOrder);
    }

    @Test
    public void expireOrder_WhenOrderDoesNotExist_ShouldThrowOrderNotFoundException() {
        assertThrows(OrderNotFoundException.class, () -> orderService.expireOrder(orderId));
    }

    @Test
    public void refundOrder_WhenOrderExists_ShouldRefundOrder() {
        fakeOrder.markAsPaid();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));

        orderService.refundOrder(orderId);

        assertEquals(OrderStatus.REFUNDED, fakeOrder.getStatus());
        verify(orderRepository).save(fakeOrder);
    }

    @Test
    public void refundOrder_WhenOrderDoesNotExist_ShouldThrowOrderNotFoundException() {
        assertThrows(OrderNotFoundException.class, () -> orderService.refundOrder(orderId));
    }

}
