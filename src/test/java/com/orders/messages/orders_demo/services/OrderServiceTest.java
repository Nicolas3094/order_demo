package com.orders.messages.orders_demo.services;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orders.messages.orders_demo.dtos.request.CreateOrderRequest;
import com.orders.messages.orders_demo.entity.Customer;
import com.orders.messages.orders_demo.entity.Order;
import com.orders.messages.orders_demo.entity.OrderItem;
import com.orders.messages.orders_demo.entity.Product;
import com.orders.messages.orders_demo.enums.Currency;
import com.orders.messages.orders_demo.enums.CustomerStatus;
import com.orders.messages.orders_demo.enums.OrderStatus;
import com.orders.messages.orders_demo.exceptions.customer.CustomerNotFoundException;
import com.orders.messages.orders_demo.exceptions.orders.OrderNotFoundException;
import com.orders.messages.orders_demo.exceptions.product.ProductNotFoundException;
import com.orders.messages.orders_demo.repositories.CustomerRepository;
import com.orders.messages.orders_demo.repositories.OrderRepository;
import com.orders.messages.orders_demo.repositories.ProductRepository;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    private Order fakeOrder;
    private UUID orderId;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    public void setup() {
        fakeOrder = Order.builder().customer(new Customer("email", "name")).build();
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
        Exception result = assertThrows(OrderNotFoundException.class, () -> orderService.getOrder(orderId));

        assertEquals("Order could not be found.", result.getMessage());
    }

    @Test
    public void createOrder_WhenCustomerExists_ShouldSaveOrder() {
        UUID customerId = UUID.randomUUID();
        Customer customer = new Customer(customerId, "email", "name", CustomerStatus.ACTIVE);
        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                .setCustomerId(customerId)
                .setCurrency(Currency.MXN)
                .build();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.createOrder(orderRequest);

        assertEquals(customer, result.getCustomer());
        assertEquals(orderRequest.currency(), result.getCurrency());
        assertEquals(BigDecimal.ZERO, result.getAmountTotal());
        assertEquals(OrderStatus.PENDING_PAYMENT, result.getStatus());
    }

    @Test
    public void createOrder_WhenCustomerNotExists_ShouldThrowCustomerNotFoundException() {
        UUID customerId = UUID.randomUUID();
        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                .setCustomerId(customerId)
                .build();

        Exception result = assertThrows(CustomerNotFoundException.class, () -> orderService.createOrder(orderRequest));

        assertEquals("Customer could not be found.", result.getMessage());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    public void cancelOrder_WhenOrderExists_ShouldCancelOrder() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));
        when(orderRepository.save(fakeOrder)).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.cancelOrder(orderId);

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        verify(orderRepository).save(fakeOrder);
    }

    @Test
    public void cancelOrder_WhenOrderExists_ShouldIncreaseStockForEachOrderItem() {
        Product product1 = Product.builder()
                .name("Product 1")
                .sku("SKU1")
                .description("Product 1 description")
                .price(BigDecimal.valueOf(10))
                .quantity(5L)
                .build();
        Product product2 = Product.builder()
                .name("Product 2")
                .sku("SKU2")
                .description("Product 2 description")
                .price(BigDecimal.valueOf(20))
                .quantity(3L)
                .build();
        OrderItem item1 = OrderItem.builder()
                .sku(product1.getSku())
                .quantity(2L)
                .unitPrice(product1.getPrice())
                .build();
        OrderItem item2 = OrderItem.builder()
                .sku(product2.getSku())
                .quantity(1L)
                .unitPrice(product2.getPrice())
                .build();
        fakeOrder.addItem(item1);
        fakeOrder.addItem(item2);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));
        when(productRepository.findBySku(product1.getSku())).thenReturn(Optional.of(product1));
        when(productRepository.findBySku(product2.getSku())).thenReturn(Optional.of(product2));
        when(orderRepository.save(fakeOrder)).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.cancelOrder(orderId);

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        assertEquals(7, product1.getQuantity());
        assertEquals(4, product2.getQuantity());
        verify(orderRepository).save(fakeOrder);
        verify(productRepository, times(2)).save(any(Product.class));
    }

    @Test
    public void cancelOrder_WhenProductNotFound_ShouldThrowProductNotFoundException() {
        Product product1 = Product.builder()
                .name("Product 1")
                .sku("SKU1")
                .description("Product 1 description")
                .price(BigDecimal.valueOf(10))
                .quantity(5L)
                .build();
        OrderItem item1 = OrderItem.builder()
                .sku(product1.getSku())
                .quantity(2L)
                .unitPrice(product1.getPrice())
                .build();
        fakeOrder.addItem(item1);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));
        when(productRepository.findBySku(product1.getSku())).thenReturn(Optional.empty());

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> orderService.cancelOrder(orderId));

        assertEquals("Product with SKU SKU1 could not be found.", result.getMessage());
        verify(orderRepository, never()).save(any(Order.class));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    public void cancelOrder_WhenOrderDoesNotExist_ShouldThrowOrderNotFoundException() {
        Exception result = assertThrows(OrderNotFoundException.class, () -> orderService.cancelOrder(orderId));

        assertEquals("Order could not be found.", result.getMessage());
    }

    @Test
    public void expireOrder_WhenOrderExists_ShouldExpireOrder() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));
        when(orderRepository.save(fakeOrder)).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.expireOrder(orderId);

        assertEquals(OrderStatus.EXPIRED, result.getStatus());
        verify(orderRepository).save(fakeOrder);
    }

    @Test
    public void expireOrder_WhenOrderDoesNotExist_ShouldThrowOrderNotFoundException() {
        Exception result = assertThrows(OrderNotFoundException.class, () -> orderService.expireOrder(orderId));

        assertEquals("Order could not be found.", result.getMessage());
    }

    @Test
    public void refundOrder_WhenOrderExists_ShouldRefundOrder() {
        fakeOrder.markAsPaid();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));
        when(orderRepository.save(fakeOrder)).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.refundOrder(orderId);

        assertEquals(OrderStatus.REFUNDED, result.getStatus());
        verify(orderRepository).save(fakeOrder);
    }

    @Test
    public void refundOrder_WhenOrderDoesNotExist_ShouldThrowOrderNotFoundException() {
        Exception result = assertThrows(OrderNotFoundException.class, () -> orderService.refundOrder(orderId));

        assertEquals("Order could not be found.", result.getMessage());
    }

}
