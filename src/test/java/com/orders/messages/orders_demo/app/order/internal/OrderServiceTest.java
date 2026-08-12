package com.orders.messages.orders_demo.app.order.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.customer.api.CustomerInternalApi;
import com.orders.messages.orders_demo.app.customer.api.CustomerResponse;
import com.orders.messages.orders_demo.app.customer.internal.CustomerStatus;
import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerNotFoundException;
import com.orders.messages.orders_demo.app.order.api.CreateOrderRequest;
import com.orders.messages.orders_demo.app.order.api.OrderResponse;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderNotFoundException;
import com.orders.messages.orders_demo.app.product.api.ProductInternalApi;
import com.orders.messages.orders_demo.app.product.internal.exceptions.ProductNotFoundException;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    private OrderEntity fakeOrder;
    private UUID orderId;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CustomerInternalApi customerInternalApi;
    @Mock
    private ProductInternalApi productInternalApi;

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    public void setup() {
        fakeOrder = OrderEntity.builder()
                .id(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .build();
        orderId = fakeOrder.getId();
    }

    @Test
    public void getAllOrders_ShouldReturnListOfOrders() {
        OrderEntity order1 = createOrder(OrderStatus.PENDING_PAYMENT);
        OrderEntity order2 = createOrder(OrderStatus.PAID);
        when(orderRepository.findAll()).thenReturn(List.of(order1, order2));

        List<OrderResponse> result = orderService.getAllOrders();

        assertEquals(2, result.size());
        assertEquals(order1.getId(), result.get(0).id());
        assertEquals(order2.getId(), result.get(1).id());
        verify(orderRepository).findAll();
    }

    /*
     * 
     * getOrder
     * 
     */

    @Test
    public void getOrder_WhenOrderExists_ReturnsOrder() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));

        OrderResponse result = orderService.getOrder(orderId);

        assertEquals(orderId, result.id());

    }

    @Test
    public void getOrder_WhenOrderDoesNotExist_ThrowsOrderNotFoundException() {
        Exception result = assertThrows(OrderNotFoundException.class, () -> orderService.getOrder(orderId));

        assertEquals("Order could not be found.", result.getMessage());
    }

    /*
     * 
     * createOrder
     * 
     */

    @Test
    public void createOrder_WhenCustomerExists_ShouldSaveOrder() {
        UUID customerId = UUID.randomUUID();
        CustomerResponse customerResponse = CustomerResponse.builder()
                .id(customerId)
                .email("email")
                .name("name")
                .status(CustomerStatus.ACTIVE)
                .build();
        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                .customerId(customerId)
                .currency(Currency.MXN)
                .build();
        when(customerInternalApi.getCustomer(customerId)).thenReturn(customerResponse);
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse result = orderService.createOrder(orderRequest);

        assertEquals(customerId, result.customerId());
        assertEquals(orderRequest.currency(), result.currency());
        assertEquals(BigDecimal.ZERO, result.amountTotal());
        assertEquals(OrderStatus.PENDING_PAYMENT, result.status());
    }

    @Test
    public void createOrder_WhenCustomerNotExists_ShouldThrowCustomerNotFoundException() {
        UUID customerId = UUID.randomUUID();
        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                .customerId(customerId)
                .currency(Currency.MXN)
                .build();
        when(customerInternalApi.getCustomer(customerId)).thenThrow(new CustomerNotFoundException());

        Exception result = assertThrows(CustomerNotFoundException.class, () -> orderService.createOrder(orderRequest));

        assertEquals("Customer could not be found.", result.getMessage());
        verify(orderRepository, never()).save(any(OrderEntity.class));
    }

    /*
     * 
     * cancelOrder
     * 
     */

    @Test
    public void cancelOrder_WhenOrderExists_ShouldCancelOrder() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));
        when(orderRepository.save(fakeOrder)).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse result = orderService.cancelOrder(orderId);

        assertEquals(OrderStatus.CANCELLED, result.status());
        verify(orderRepository).save(fakeOrder);
    }

    @Test
    public void cancelOrder_WhenOrderExists_ShouldIncreaseStockForEachOrderItem() {
        OrderItemEntity item1 = createOrderItem("SKU1", 2L);
        OrderItemEntity item2 = createOrderItem("SKU2", 1L);
        fakeOrder.addItem(item1);
        fakeOrder.addItem(item2);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));
        doNothing().when(productInternalApi).increaceProductStock("SKU1", 2L);
        doNothing().when(productInternalApi).increaceProductStock("SKU2", 1L);
        when(orderRepository.save(fakeOrder)).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse result = orderService.cancelOrder(orderId);

        verify(orderRepository).save(fakeOrder);
        verify(productInternalApi).increaceProductStock("SKU1", 2L);
        verify(productInternalApi).increaceProductStock("SKU2", 1L);
        assertEquals(OrderStatus.CANCELLED, result.status());
    }

    @Test
    public void cancelOrder_WhenProductNotFound_ShouldThrowProductNotFoundException() {
        OrderItemEntity item1 = createOrderItem("SKU1", 2L);
        fakeOrder.addItem(item1);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));
        doThrow(new ProductNotFoundException("SKU1"))
                .when(productInternalApi).increaceProductStock("SKU1", 2L);

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> orderService.cancelOrder(orderId));

        verify(orderRepository, never()).save(any(OrderEntity.class));
        verify(productInternalApi).increaceProductStock("SKU1", 2L);
        assertEquals("Product with SKU SKU1 could not be found.", result.getMessage());
    }

    @Test
    public void cancelOrder_WhenOrderDoesNotExist_ShouldThrowOrderNotFoundException() {
        Exception result = assertThrows(OrderNotFoundException.class, () -> orderService.cancelOrder(orderId));

        assertEquals("Order could not be found.", result.getMessage());
    }

    /*
     * 
     * expireOrder
     * 
     */

    @Test
    public void expireOrder_WhenOrderExists_ShouldExpireOrder() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));
        when(orderRepository.save(fakeOrder)).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse result = orderService.expireOrder(orderId);

        verify(orderRepository).save(fakeOrder);
        assertEquals(OrderStatus.EXPIRED, result.status());
    }

    @Test
    public void expireOrder_WhenOrderExists_ShouldIncreaseStockForEachOrderItem() {
        OrderItemEntity item1 = createOrderItem("SKU1", 2L);
        OrderItemEntity item2 = createOrderItem("SKU2", 1L);
        fakeOrder.addItem(item1);
        fakeOrder.addItem(item2);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));
        doNothing().when(productInternalApi).increaceProductStock("SKU1", 2L);
        doNothing().when(productInternalApi).increaceProductStock("SKU2", 1L);
        when(orderRepository.save(fakeOrder)).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse result = orderService.expireOrder(orderId);

        verify(orderRepository).save(fakeOrder);
        verify(productInternalApi).increaceProductStock("SKU1", 2L);
        verify(productInternalApi).increaceProductStock("SKU2", 1L);
        assertEquals(OrderStatus.EXPIRED, result.status());
    }

    @Test
    public void expireOrder_WhenProductNotFound_ShouldThrowProductNotFoundException() {
        OrderItemEntity item1 = createOrderItem("SKU1", 2L);
        fakeOrder.addItem(item1);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));
        doThrow(new ProductNotFoundException("SKU1"))
                .when(productInternalApi).increaceProductStock("SKU1", 2L);

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> orderService.expireOrder(orderId));

        verify(orderRepository, never()).save(any(OrderEntity.class));
        verify(productInternalApi).increaceProductStock("SKU1", 2L);
        assertEquals("Product with SKU SKU1 could not be found.", result.getMessage());
    }

    @Test
    public void expireOrder_WhenOrderDoesNotExist_ShouldThrowOrderNotFoundException() {
        Exception result = assertThrows(OrderNotFoundException.class, () -> orderService.expireOrder(orderId));

        assertEquals("Order could not be found.", result.getMessage());
    }

    /*
     * 
     * refundOrder
     * 
     */

    @Test
    public void refundOrder_WhenOrderExists_ShouldRefundOrder() {
        fakeOrder.markAsPaid();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));
        when(orderRepository.save(fakeOrder)).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse result = orderService.refundOrder(orderId);

        assertEquals(OrderStatus.REFUNDED, result.status());
        verify(orderRepository).save(fakeOrder);
    }

    @Test
    public void refundOrder_WhenOrderExists_ShouldIncreaseStockForEachOrderItem() {
        OrderItemEntity item1 = createOrderItem("SKU1", 2L);
        OrderItemEntity item2 = createOrderItem("SKU2", 1L);
        fakeOrder.addItem(item1);
        fakeOrder.addItem(item2);
        fakeOrder.markAsPaid();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));
        doNothing().when(productInternalApi).increaceProductStock("SKU1", 2L);
        doNothing().when(productInternalApi).increaceProductStock("SKU2", 1L);
        when(orderRepository.save(fakeOrder)).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse result = orderService.refundOrder(orderId);

        verify(orderRepository).save(fakeOrder);
        verify(productInternalApi).increaceProductStock("SKU1", 2L);
        verify(productInternalApi).increaceProductStock("SKU2", 1L);
        assertEquals(OrderStatus.REFUNDED, result.status());
    }

    @Test
    public void refundOrder_WhenProductNotFound_ShouldThrowProductNotFoundException() {
        OrderItemEntity item1 = createOrderItem("SKU1", 2L);
        fakeOrder.addItem(item1);
        fakeOrder.markAsPaid();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));
        doThrow(new ProductNotFoundException("SKU1"))
                .when(productInternalApi).increaceProductStock("SKU1", 2L);

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> orderService.refundOrder(orderId));

        verify(orderRepository, never()).save(any(OrderEntity.class));
        verify(productInternalApi).increaceProductStock("SKU1", 2L);
        assertEquals("Product with SKU SKU1 could not be found.", result.getMessage());

    }

    @Test
    public void refundOrder_WhenOrderDoesNotExist_ShouldThrowOrderNotFoundException() {
        Exception result = assertThrows(OrderNotFoundException.class, () -> orderService.refundOrder(orderId));

        assertEquals("Order could not be found.", result.getMessage());
    }

    private OrderEntity createOrder(OrderStatus status) {
        return OrderEntity.builder()
                .id(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .status(status)
                .build();
    }

    private OrderItemEntity createOrderItem(String sku, Long quantity) {
        return OrderItemEntity.builder()
                .id(UUID.randomUUID())
                .sku(sku)
                .quantity(quantity)
                .unitPrice(BigDecimal.valueOf(10))
                .build();
    }

}
