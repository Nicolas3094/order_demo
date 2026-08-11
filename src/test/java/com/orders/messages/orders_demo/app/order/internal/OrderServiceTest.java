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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerRepository;
import com.orders.messages.orders_demo.app.customer.internal.CustomerStatus;
import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerNotFoundException;
import com.orders.messages.orders_demo.app.order.api.CreateOrderRequest;
import com.orders.messages.orders_demo.app.order.api.OrderResponse;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderNotFoundException;
import com.orders.messages.orders_demo.app.order_item.internal.OrderItemEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductRepository;
import com.orders.messages.orders_demo.app.product.internal.exceptions.ProductNotFoundException;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    private OrderEntity fakeOrder;
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
        fakeOrder = OrderEntity.builder()
                .id(UUID.randomUUID())
                .customer(new CustomerEntity("email", "name"))
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
        CustomerEntity customer = new CustomerEntity(customerId, "email", "name", CustomerStatus.ACTIVE);
        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                .customerId(customerId)
                .currency(Currency.MXN)
                .build();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
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
        ProductEntity product1 = createProduct("SKU1", 5L);
        ProductEntity product2 = createProduct("SKU2", 3L);
        OrderItemEntity item1 = createOrderItem(product1, 2L);
        OrderItemEntity item2 = createOrderItem(product2, 1L);
        fakeOrder.addItem(item1);
        fakeOrder.addItem(item2);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));
        when(productRepository.findBySku(product1.getSku())).thenReturn(Optional.of(product1));
        when(productRepository.findBySku(product2.getSku())).thenReturn(Optional.of(product2));
        when(orderRepository.save(fakeOrder)).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse result = orderService.cancelOrder(orderId);

        assertEquals(OrderStatus.CANCELLED, result.status());
        assertEquals(7, product1.getQuantity());
        assertEquals(4, product2.getQuantity());
        verify(orderRepository).save(fakeOrder);
        verify(productRepository, times(2)).save(any(ProductEntity.class));
    }

    @Test
    public void cancelOrder_WhenProductNotFound_ShouldThrowProductNotFoundException() {
        ProductEntity product1 = createProduct("SKU1", 5L);
        OrderItemEntity item1 = createOrderItem(product1, 2L);
        fakeOrder.addItem(item1);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));
        when(productRepository.findBySku(product1.getSku())).thenReturn(Optional.empty());

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> orderService.cancelOrder(orderId));

        assertEquals("Product with SKU SKU1 could not be found.", result.getMessage());
        verify(orderRepository, never()).save(any(OrderEntity.class));
        verify(productRepository, never()).save(any(ProductEntity.class));
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

        assertEquals(OrderStatus.EXPIRED, result.status());
        verify(orderRepository).save(fakeOrder);
    }

    @Test
    public void expireOrder_WhenOrderExists_ShouldIncreaseStockForEachOrderItem() {
        ProductEntity product1 = createProduct("SKU1", 5L);
        ProductEntity product2 = createProduct("SKU2", 3L);
        OrderItemEntity item1 = createOrderItem(product1, 2L);
        OrderItemEntity item2 = createOrderItem(product2, 1L);
        fakeOrder.addItem(item1);
        fakeOrder.addItem(item2);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));
        when(productRepository.findBySku(product1.getSku())).thenReturn(Optional.of(product1));
        when(productRepository.findBySku(product2.getSku())).thenReturn(Optional.of(product2));
        when(orderRepository.save(fakeOrder)).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse result = orderService.expireOrder(orderId);

        assertEquals(OrderStatus.EXPIRED, result.status());
        assertEquals(7, product1.getQuantity());
        assertEquals(4, product2.getQuantity());
        verify(orderRepository).save(fakeOrder);
        verify(productRepository, times(2)).save(any(ProductEntity.class));
    }

    @Test
    public void expireOrder_WhenProductNotFound_ShouldThrowProductNotFoundException() {
        ProductEntity product1 = createProduct("SKU1", 5L);
        OrderItemEntity item1 = createOrderItem(product1, 2L);
        fakeOrder.addItem(item1);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));
        when(productRepository.findBySku(product1.getSku())).thenReturn(Optional.empty());

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> orderService.expireOrder(orderId));

        assertEquals("Product with SKU SKU1 could not be found.", result.getMessage());
        verify(orderRepository, never()).save(any(OrderEntity.class));
        verify(productRepository, never()).save(any(ProductEntity.class));
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
        ProductEntity product1 = createProduct("SKU1", 5L);
        ProductEntity product2 = createProduct("SKU2", 3L);
        OrderItemEntity item1 = createOrderItem(product1, 2L);
        OrderItemEntity item2 = createOrderItem(product2, 1L);
        fakeOrder.addItem(item1);
        fakeOrder.addItem(item2);
        fakeOrder.markAsPaid();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));
        when(productRepository.findBySku(product1.getSku())).thenReturn(Optional.of(product1));
        when(productRepository.findBySku(product2.getSku())).thenReturn(Optional.of(product2));
        when(orderRepository.save(fakeOrder)).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse result = orderService.refundOrder(orderId);

        assertEquals(OrderStatus.REFUNDED, result.status());
        assertEquals(7, product1.getQuantity());
        assertEquals(4, product2.getQuantity());
        verify(orderRepository).save(fakeOrder);
        verify(productRepository, times(2)).save(any(ProductEntity.class));
    }

    @Test
    public void refundOrder_WhenProductNotFound_ShouldThrowProductNotFoundException() {
        ProductEntity product1 = createProduct("SKU1", 5L);
        OrderItemEntity item1 = createOrderItem(product1, 2L);
        fakeOrder.addItem(item1);
        fakeOrder.markAsPaid();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));
        when(productRepository.findBySku(product1.getSku())).thenReturn(Optional.empty());

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> orderService.refundOrder(orderId));

        assertEquals("Product with SKU SKU1 could not be found.", result.getMessage());
        verify(orderRepository, never()).save(any(OrderEntity.class));
        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    @Test
    public void refundOrder_WhenOrderDoesNotExist_ShouldThrowOrderNotFoundException() {
        Exception result = assertThrows(OrderNotFoundException.class, () -> orderService.refundOrder(orderId));

        assertEquals("Order could not be found.", result.getMessage());
    }

    private OrderEntity createOrder(OrderStatus status) {
        OrderEntity order = OrderEntity.builder()
                .id(UUID.randomUUID())
                .customer(new CustomerEntity("email", "name"))
                .status(status)
                .build();
        return order;
    }

    private OrderItemEntity createOrderItem(ProductEntity product, Long quantity) {
        return OrderItemEntity.builder()
                .id(UUID.randomUUID())
                .sku(product.getSku())
                .quantity(quantity)
                .unitPrice(product.getPrice())
                .build();
    }

    private ProductEntity createProduct(String sku, Long quantity) {
        return ProductEntity.builder()
                .id(UUID.randomUUID())
                .name(sku)
                .sku(sku)
                .price(BigDecimal.valueOf(10))
                .quantity(quantity)
                .build();
    }
}
