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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orders.messages.orders_demo.dtos.request.CreateOrderItemRequest;
import com.orders.messages.orders_demo.entity.Customer;
import com.orders.messages.orders_demo.entity.Order;
import com.orders.messages.orders_demo.entity.OrderItem;
import com.orders.messages.orders_demo.entity.Product;
import com.orders.messages.orders_demo.enums.Currency;
import com.orders.messages.orders_demo.enums.OrderStatus;
import com.orders.messages.orders_demo.exceptions.order_item.OrderItemNotFoundException;
import com.orders.messages.orders_demo.exceptions.orders.InvalidOrderStateException;
import com.orders.messages.orders_demo.exceptions.orders.OrderNotFoundException;
import com.orders.messages.orders_demo.exceptions.product.InsufficientStockException;
import com.orders.messages.orders_demo.exceptions.product.InvalidProductException;
import com.orders.messages.orders_demo.exceptions.product.ProductNotFoundException;
import com.orders.messages.orders_demo.repositories.OrderItemRepository;
import com.orders.messages.orders_demo.repositories.OrderRepository;
import com.orders.messages.orders_demo.repositories.ProductRepository;

@ExtendWith(MockitoExtension.class)
public class OrderItemServiceTest {

    private static final String ORDER_ERROR_MESSAGE = "Order could not be found.";
    private static final String ORDER_ITEM_ERROR_MESSAGE = "Order item not found.";
    private static final String DEFAULT_SKU = "sku";
    private static final String DEFAULT_DESCRIPTION = "description";
    private static final BigDecimal DEFAULT_UNIT_PRICE = new BigDecimal("12.00");
    private static final Long DEFAULT_QUANTITY_LONG = 10L;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private OrderItemService orderItemService;

    private UUID orderItemId;
    private UUID orderId;

    @BeforeEach
    public void setup() {
        orderId = UUID.randomUUID();
        orderItemId = UUID.randomUUID();
    }

    /*
     * 
     * getOrderItem
     * 
     */

    @Test
    public void getOrderItem_WhenOrderItemFound_ShouldGetOrderItem() {
        Order order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        OrderItem orderItem = createOrderItem();
        order.addItem(orderItem);
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(orderItem));

        OrderItem result = orderItemService.getOrderItem(orderId, orderItemId);

        assertEquals(orderItem, result);
        assertEquals(orderItem.getOrder(), result.getOrder());
        verify(orderItemRepository).findById(orderItemId);
    }

    @Test
    public void getOrderItem_WhenOrderItemNotFound_ShouldThrowOrderItemNotFoundException() {
        OrderItemNotFoundException result = assertThrows(OrderItemNotFoundException.class,
                () -> orderItemService.getOrderItem(orderId, orderItemId));

        assertEquals(ORDER_ITEM_ERROR_MESSAGE, result.getMessage());
    }

    /*
     * 
     * createOrderItem
     * 
     */

    @Test
    public void createOrderItem_WhenOrderAndProductFound_ShouldCreateOrderItem() {
        CreateOrderItemRequest request = createOrderItemRequest();
        Order order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        Product product = createProduct(true, 20L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderItem result = orderItemService.createOrderItem(orderId, request);

        assertEquals(10L, product.getQuantity());
        assertEquals(order, result.getOrder());
        assertEquals(1, order.getItems().size());
        assertEquals(DEFAULT_SKU, result.getSku());
        assertEquals(DEFAULT_DESCRIPTION, result.getDescription());
        assertEquals(DEFAULT_UNIT_PRICE, result.getUnitPrice());
        assertEquals(DEFAULT_QUANTITY_LONG, result.getQuantity());
        verify(orderRepository).save(order);
        verify(productRepository).save(product);
    }

    @Test
    public void createOrderItem_WhenOrderNotFound_ShouldThrowOrderNotFoundException() {
        CreateOrderItemRequest request = createOrderItemRequest();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        OrderNotFoundException result = assertThrows(OrderNotFoundException.class,
                () -> orderItemService.createOrderItem(orderId, request));

        assertEquals(ORDER_ERROR_MESSAGE, result.getMessage());
        verify(orderRepository, never()).save(any(Order.class));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    public void createOrderItem_WhenProductNotFound_ShouldThrowProductNotFoundException() {
        CreateOrderItemRequest request = createOrderItemRequest();
        Order order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.empty());

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> orderItemService.createOrderItem(orderId, request));

        assertEquals("Product with SKU sku could not be found.", result.getMessage());
        verify(orderRepository, never()).save(any(Order.class));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    public void createOrderItem_WhenProductIsNotActive_ShouldThrowInvalidProductException() {
        CreateOrderItemRequest request = createOrderItemRequest();
        Order order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        Product product = createProduct(false, 20L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.of(product));

        InvalidProductException result = assertThrows(InvalidProductException.class,
                () -> orderItemService.createOrderItem(orderId, request));

        assertEquals("Product with SKU sku is not active.", result.getMessage());
        verify(orderRepository, never()).save(any(Order.class));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    public void createOrderItem_WhenProductDoesNotHaveEnoughStock_ShouldThrowInvalidProductException() {
        CreateOrderItemRequest request = createOrderItemRequestWithQuantity(3L);
        Order order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        Product product = createProduct(true, 2L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.of(product));

        InsufficientStockException result = assertThrows(InsufficientStockException.class,
                () -> orderItemService.createOrderItem(orderId, request));

        assertEquals("Insufficient stock.", result.getMessage());
        verify(orderRepository, never()).save(any(Order.class));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    public void createOrderItem_WhenProductCurrencyDoesNotMatchOrderCurrency_ShouldThrowInvalidProductException() {
        CreateOrderItemRequest request = createOrderItemRequest();
        Order order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        Product product = Product.builder()
                .quantity(100L)
                .name("product_name")
                .sku(DEFAULT_SKU)
                .description(DEFAULT_DESCRIPTION)
                .price(DEFAULT_UNIT_PRICE)
                .active(true)
                .currency(Currency.USD)
                .build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.of(product));

        InvalidOrderStateException result = assertThrows(InvalidOrderStateException.class,
                () -> orderItemService.createOrderItem(orderId, request));

        assertEquals("Product currency USD does not match order currency MXN.", result.getMessage());
        verify(orderRepository, never()).save(any(Order.class));
        verify(productRepository, never()).save(any(Product.class));
    }

    /*
     * 
     * deleteOrderItem
     * 
     */

    @Test
    public void deleteOrderItem_WhenOrderItemAndProductAreFound_ShouldDeleteOrderItem() {
        Order order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        OrderItem orderItem = createOrderItem();
        Product product = createProduct(true, 2L);
        order.addItem(orderItem);
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(orderItem));
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.of(product));

        orderItemService.deleteOrderItem(orderId, orderItemId);

        assertEquals(12L, product.getQuantity());
        assertEquals(0, order.getItems().size());
        verify(orderItemRepository).findById(orderItemId);
        verify(productRepository).findBySku(DEFAULT_SKU);
        verify(productRepository).save(product);
        verify(orderRepository).save(order);
        verify(orderItemRepository, never()).delete(any(OrderItem.class));

    }

    @Test
    public void deleteOrderItem_WhenOrderItemNotFound_ShouldThrowOrderItemNotFoundException() {

        OrderItemNotFoundException result = assertThrows(OrderItemNotFoundException.class,
                () -> orderItemService.deleteOrderItem(orderId, orderItemId));

        assertEquals(ORDER_ITEM_ERROR_MESSAGE, result.getMessage());
        verify(orderItemRepository, never()).delete(any(OrderItem.class));
    }

    @Test
    public void deleteOrderItem_WhenProductIsNotFound_ShouldThrowProductNotFoundException() {
        Order order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        OrderItem orderItem = createOrderItem();
        order.addItem(orderItem);
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(orderItem));
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.empty());

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> orderItemService.deleteOrderItem(orderId, orderItemId));

        assertEquals("Product with SKU sku could not be found.", result.getMessage());
        verify(orderItemRepository, never()).delete(any(OrderItem.class));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    public void deleteOrderItem_WhenOrderIdNotEqual_ShouldThrowOrderItemNotFoundException() {
        UUID differentOrderId = UUID.randomUUID();
        Order order = createOrder(differentOrderId, OrderStatus.PENDING_PAYMENT);
        OrderItem orderItem = createOrderItem();
        order.addItem(orderItem);
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(orderItem));

        OrderItemNotFoundException result = assertThrows(OrderItemNotFoundException.class,
                () -> orderItemService.deleteOrderItem(orderId, orderItemId));

        assertEquals(ORDER_ITEM_ERROR_MESSAGE, result.getMessage());
        verify(orderItemRepository, never()).delete(any(OrderItem.class));
    }

    /*
     * 
     * changeUnitPrice
     * 
     */

    @Test
    public void changeUnitPrice_WhenOrderItemFound_ShouldChangeUnitPrice() {
        BigDecimal newUnitPrice = new BigDecimal("8.00");
        Order order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        OrderItem orderItem = createOrderItem();
        order.addItem(orderItem);
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(orderItem));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invoke -> invoke.getArgument(0));

        OrderItem result = orderItemService.changeUnitPrice(orderId, orderItemId, newUnitPrice);

        assertEquals(orderItem.getOrder(), result.getOrder());
        assertEquals(DEFAULT_SKU, result.getSku());
        assertEquals(DEFAULT_DESCRIPTION, result.getDescription());
        assertEquals(newUnitPrice, result.getUnitPrice());
        assertEquals(DEFAULT_QUANTITY_LONG, result.getQuantity());
        verify(orderItemRepository).findById(orderItemId);
        verify(orderItemRepository).save(result);
    }

    @Test
    public void changeUnitPrice_WhenOrderItemNotFound_ShouldThrowOrderItemNotFoundException() {
        BigDecimal newUnitPrice = new BigDecimal("8.00");
        OrderItemNotFoundException result = assertThrows(OrderItemNotFoundException.class,
                () -> orderItemService.changeUnitPrice(orderId, orderItemId, newUnitPrice));

        assertEquals(ORDER_ITEM_ERROR_MESSAGE, result.getMessage());
        verify(orderItemRepository, never()).save(any(OrderItem.class));
    }

    @Test
    public void changeUnitPrice_WhenOrderIdNotEqual_ShouldThrowOrderItemNotFoundException() {
        BigDecimal newUnitPrice = new BigDecimal("8.00");
        UUID differentOrderId = UUID.randomUUID();
        Order order = createOrder(differentOrderId, OrderStatus.PENDING_PAYMENT);
        OrderItem orderItem = createOrderItem();
        order.addItem(orderItem);
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(orderItem));

        OrderItemNotFoundException result = assertThrows(OrderItemNotFoundException.class,
                () -> orderItemService.changeUnitPrice(orderId, orderItemId, newUnitPrice));

        assertEquals(ORDER_ITEM_ERROR_MESSAGE, result.getMessage());
        verify(orderItemRepository, never()).save(any(OrderItem.class));
    }

    /*
     * 
     * changeQuantity
     * 
     */

    @Test
    public void changeQuantity_WhenOrderItemFound_ShouldChangeQuantity() {
        Long newQuantity = 22L;
        Order order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        OrderItem orderItem = createOrderItem();
        order.addItem(orderItem);
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(orderItem));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invoke -> invoke.getArgument(0));

        OrderItem result = orderItemService.changeQuantity(orderId, orderItemId, newQuantity);

        assertEquals(orderItem.getOrder(), result.getOrder());
        assertEquals(DEFAULT_SKU, result.getSku());
        assertEquals(DEFAULT_DESCRIPTION, result.getDescription());
        assertEquals(DEFAULT_UNIT_PRICE, result.getUnitPrice());
        assertEquals(newQuantity, result.getQuantity());
        verify(orderItemRepository).findById(orderItemId);
        verify(orderItemRepository).save(result);
    }

    @Test
    public void changeQuantity_WhenOrderItemNotFound_ShouldThrowOrderItemNotFoundException() {
        Long newQuantity = 22L;
        OrderItemNotFoundException result = assertThrows(OrderItemNotFoundException.class,
                () -> orderItemService.changeQuantity(orderId, orderItemId, newQuantity));

        assertEquals(ORDER_ITEM_ERROR_MESSAGE, result.getMessage());
        verify(orderItemRepository, never()).save(any(OrderItem.class));
    }

    @Test
    public void changeQuantity_WhenOrderIdNotEqual_ShouldThrowOrderItemNotFoundException() {
        Long newQuantity = 22L;
        UUID differentOrderId = UUID.randomUUID();
        Order order = createOrder(differentOrderId, OrderStatus.PENDING_PAYMENT);
        OrderItem orderItem = createOrderItem();
        order.addItem(orderItem);
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(orderItem));

        OrderItemNotFoundException result = assertThrows(OrderItemNotFoundException.class,
                () -> orderItemService.changeQuantity(orderId, orderItemId, newQuantity));

        assertEquals(ORDER_ITEM_ERROR_MESSAGE, result.getMessage());
        verify(orderItemRepository, never()).save(any(OrderItem.class));
    }

    private static CreateOrderItemRequest createOrderItemRequestWithQuantity(Long quantity) {
        return new CreateOrderItemRequest(DEFAULT_SKU, quantity);
    }

    private static CreateOrderItemRequest createOrderItemRequest() {
        return new CreateOrderItemRequest(DEFAULT_SKU, DEFAULT_QUANTITY_LONG);
    }

    private static Product createProduct(boolean isActive, Long quantity) {
        return Product.builder()
                .quantity(quantity)
                .name("product_name")
                .sku(DEFAULT_SKU)
                .description(DEFAULT_DESCRIPTION)
                .price(DEFAULT_UNIT_PRICE)
                .active(isActive)
                .build();
    }

    private static OrderItem createOrderItem() {
        return OrderItem.builder()
                .sku(DEFAULT_SKU)
                .description(DEFAULT_DESCRIPTION)
                .unitPrice(DEFAULT_UNIT_PRICE)
                .quantity(DEFAULT_QUANTITY_LONG)
                .build();

    }

    private static Order createOrder(UUID orderId, OrderStatus orderStatus) {
        return Order.builder()
                .id(orderId)
                .customer(new Customer("email", "name"))
                .currency(Currency.MXN)
                .status(orderStatus)
                .build();
    }

}
