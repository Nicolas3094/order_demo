package com.orders.messages.orders_demo.app.order_item.internal;

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

import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.order.internal.OrderEntity;
import com.orders.messages.orders_demo.app.order.internal.OrderRepository;
import com.orders.messages.orders_demo.app.order.internal.OrderStatus;
import com.orders.messages.orders_demo.app.order.internal.exceptions.InvalidOrderStateException;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderNotFoundException;
import com.orders.messages.orders_demo.app.order_item.api.CreateOrderItemRequest;
import com.orders.messages.orders_demo.app.order_item.api.OrderItemResponse;
import com.orders.messages.orders_demo.app.order_item.internal.exceptions.OrderItemNotFoundException;
import com.orders.messages.orders_demo.app.product.internal.ProductEntity;
import com.orders.messages.orders_demo.app.product.internal.ProductRepository;
import com.orders.messages.orders_demo.app.product.internal.exceptions.InsufficientStockException;
import com.orders.messages.orders_demo.app.product.internal.exceptions.InvalidProductException;
import com.orders.messages.orders_demo.app.product.internal.exceptions.ProductNotFoundException;

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

    @Test
    public void getAllOrderItems_WhenOrderFound_ShouldGetAllOrderItems() {
        UUID orderItemId2 = UUID.randomUUID();
        OrderEntity order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        OrderItemEntity orderItem1 = createOrderItem(orderItemId);
        OrderItemEntity orderItem2 = createOrderItem(orderItemId2);
        order.addItem(orderItem1);
        order.addItem(orderItem2);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        var result = orderItemService.getAllOrderItems(orderId);

        assertEquals(2, result.size());
        assertEquals(orderItem1.getId(), result.get(0).id());
        assertEquals(orderItem2.getId(), result.get(1).id());
    }

    @Test
    public void getAllOrderItems_WhenOrderNotFound_ShouldThrowOrderNotFoundException() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        OrderNotFoundException result = assertThrows(OrderNotFoundException.class,
                () -> orderItemService.getAllOrderItems(orderId));

        assertEquals(ORDER_ERROR_MESSAGE, result.getMessage());
    }

    /*
     * 
     * getOrderItem
     * 
     */

    @Test
    public void getOrderItem_WhenOrderItemFound_ShouldGetOrderItem() {
        OrderEntity order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        OrderItemEntity orderItem = createOrderItem(orderItemId);
        order.addItem(orderItem);
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(orderItem));

        OrderItemResponse result = orderItemService.getOrderItem(orderId, orderItemId);

        verify(orderItemRepository).findById(orderItemId);
        assertEquals(orderItem.getId(), result.id());
        assertEquals(orderItem.getOrder().getId(), result.orderId());
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
        OrderEntity order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        ProductEntity product = createProduct(true, 20L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderItemResponse result = orderItemService.createOrderItem(orderId, request);

        assertEquals(10L, product.getQuantity());
        assertEquals(order.getId(), result.orderId());
        assertEquals(1, order.getItems().size());
        assertEquals(DEFAULT_SKU, result.sku());
        assertEquals(DEFAULT_DESCRIPTION, result.description());
        assertEquals(DEFAULT_UNIT_PRICE, result.unitPrice());
        assertEquals(DEFAULT_QUANTITY_LONG, result.quantity());
        verify(orderRepository).save(order);
        verify(productRepository).save(product);
    }

    @Test
    public void createOrderItem_WhenOrderNotFound_ShouldThrowOrderNotFoundException() {
        CreateOrderItemRequest request = createOrderItemRequest();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        OrderNotFoundException result = assertThrows(OrderNotFoundException.class,
                () -> orderItemService.createOrderItem(orderId, request));

        verify(orderRepository, never()).save(any(OrderEntity.class));
        verify(productRepository, never()).save(any(ProductEntity.class));
        assertEquals(ORDER_ERROR_MESSAGE, result.getMessage());
    }

    @Test
    public void createOrderItem_WhenProductNotFound_ShouldThrowProductNotFoundException() {
        CreateOrderItemRequest request = createOrderItemRequest();
        OrderEntity order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.empty());

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> orderItemService.createOrderItem(orderId, request));

        verify(orderRepository, never()).save(any(OrderEntity.class));
        verify(productRepository, never()).save(any(ProductEntity.class));
        assertEquals("Product with SKU sku could not be found.", result.getMessage());
    }

    @Test
    public void createOrderItem_WhenProductIsNotActive_ShouldThrowInvalidProductException() {
        CreateOrderItemRequest request = createOrderItemRequest();
        OrderEntity order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        ProductEntity product = createProduct(false, 20L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.of(product));

        InvalidProductException result = assertThrows(InvalidProductException.class,
                () -> orderItemService.createOrderItem(orderId, request));

        verify(orderRepository, never()).save(any(OrderEntity.class));
        verify(productRepository, never()).save(any(ProductEntity.class));
        assertEquals("Product with SKU sku is not active.", result.getMessage());
    }

    @Test
    public void createOrderItem_WhenProductDoesNotHaveEnoughStock_ShouldThrowInvalidProductException() {
        CreateOrderItemRequest request = createOrderItemRequestWithQuantity(3L);
        OrderEntity order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        ProductEntity product = createProduct(true, 2L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.of(product));

        InsufficientStockException result = assertThrows(InsufficientStockException.class,
                () -> orderItemService.createOrderItem(orderId, request));

        verify(orderRepository, never()).save(any(OrderEntity.class));
        verify(productRepository, never()).save(any(ProductEntity.class));
        assertEquals("Insufficient stock.", result.getMessage());
    }

    @Test
    public void createOrderItem_WhenProductCurrencyDoesNotMatchOrderCurrency_ShouldThrowInvalidProductException() {
        CreateOrderItemRequest request = createOrderItemRequest();
        OrderEntity order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        ProductEntity product = ProductEntity.builder()
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

        verify(orderRepository, never()).save(any(OrderEntity.class));
        verify(productRepository, never()).save(any(ProductEntity.class));
        assertEquals("Product currency USD does not match order currency MXN.", result.getMessage());
    }

    /*
     * 
     * deleteOrderItem
     * 
     */

    @Test
    public void deleteOrderItem_WhenOrderItemAndProductAreFound_ShouldDeleteOrderItem() {
        OrderEntity order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        OrderItemEntity orderItem = createOrderItem(orderItemId);
        ProductEntity product = createProduct(true, 2L);
        order.addItem(orderItem);
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(orderItem));
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.of(product));

        orderItemService.deleteOrderItem(orderId, orderItemId);

        verify(orderItemRepository).findById(orderItemId);
        verify(productRepository).findBySku(DEFAULT_SKU);
        verify(productRepository).save(product);
        verify(orderRepository).save(order);
        verify(orderItemRepository, never()).delete(any(OrderItemEntity.class));
        assertEquals(12L, product.getQuantity());
        assertEquals(0, order.getItems().size());
    }

    @Test
    public void deleteOrderItem_WhenOrderItemNotFound_ShouldThrowOrderItemNotFoundException() {

        OrderItemNotFoundException result = assertThrows(OrderItemNotFoundException.class,
                () -> orderItemService.deleteOrderItem(orderId, orderItemId));

        verify(orderItemRepository, never()).delete(any(OrderItemEntity.class));
        assertEquals(ORDER_ITEM_ERROR_MESSAGE, result.getMessage());
    }

    @Test
    public void deleteOrderItem_WhenProductIsNotFound_ShouldThrowProductNotFoundException() {
        OrderEntity order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        OrderItemEntity orderItem = createOrderItem(orderItemId);
        order.addItem(orderItem);
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(orderItem));
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.empty());

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> orderItemService.deleteOrderItem(orderId, orderItemId));

        verify(orderItemRepository, never()).delete(any(OrderItemEntity.class));
        verify(productRepository, never()).save(any(ProductEntity.class));
        assertEquals("Product with SKU sku could not be found.", result.getMessage());
    }

    @Test
    public void deleteOrderItem_WhenOrderIdNotEqual_ShouldThrowOrderItemNotFoundException() {
        UUID differentOrderId = UUID.randomUUID();
        OrderEntity order = createOrder(differentOrderId, OrderStatus.PENDING_PAYMENT);
        OrderItemEntity orderItem = createOrderItem(orderItemId);
        order.addItem(orderItem);
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(orderItem));

        OrderItemNotFoundException result = assertThrows(OrderItemNotFoundException.class,
                () -> orderItemService.deleteOrderItem(orderId, orderItemId));

        verify(orderItemRepository, never()).delete(any(OrderItemEntity.class));
        assertEquals(ORDER_ITEM_ERROR_MESSAGE, result.getMessage());
    }

    /*
     * 
     * changeUnitPrice
     * 
     */

    @Test
    public void changeUnitPrice_WhenOrderItemFound_ShouldChangeUnitPrice() {
        BigDecimal newUnitPrice = new BigDecimal("8.00");
        OrderEntity order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        OrderItemEntity orderItem = createOrderItem(orderItemId);
        order.addItem(orderItem);
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(orderItem));
        when(orderItemRepository.save(any(OrderItemEntity.class))).thenAnswer(invoke -> invoke.getArgument(0));

        OrderItemResponse result = orderItemService.changeUnitPrice(orderId, orderItemId, newUnitPrice);

        verify(orderItemRepository).findById(orderItemId);
        verify(orderItemRepository).save(orderItem);
        assertEquals(orderItem.getOrder().getId(), result.orderId());
        assertEquals(DEFAULT_SKU, result.sku());
        assertEquals(DEFAULT_DESCRIPTION, result.description());
        assertEquals(newUnitPrice, result.unitPrice());
        assertEquals(DEFAULT_QUANTITY_LONG, result.quantity());
    }

    @Test
    public void changeUnitPrice_WhenOrderItemNotFound_ShouldThrowOrderItemNotFoundException() {
        BigDecimal newUnitPrice = new BigDecimal("8.00");
        OrderItemNotFoundException result = assertThrows(OrderItemNotFoundException.class,
                () -> orderItemService.changeUnitPrice(orderId, orderItemId, newUnitPrice));

        verify(orderItemRepository, never()).save(any(OrderItemEntity.class));
        assertEquals(ORDER_ITEM_ERROR_MESSAGE, result.getMessage());
    }

    @Test
    public void changeUnitPrice_WhenOrderIdNotEqual_ShouldThrowOrderItemNotFoundException() {
        BigDecimal newUnitPrice = new BigDecimal("8.00");
        UUID differentOrderId = UUID.randomUUID();
        OrderEntity order = createOrder(differentOrderId, OrderStatus.PENDING_PAYMENT);
        OrderItemEntity orderItem = createOrderItem(orderItemId);
        order.addItem(orderItem);
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(orderItem));

        OrderItemNotFoundException result = assertThrows(OrderItemNotFoundException.class,
                () -> orderItemService.changeUnitPrice(orderId, orderItemId, newUnitPrice));

        verify(orderItemRepository, never()).save(any(OrderItemEntity.class));
        assertEquals(ORDER_ITEM_ERROR_MESSAGE, result.getMessage());
    }

    /*
     * 
     * changeQuantity
     * 
     */

    @Test
    public void changeQuantity_WhenOrderItemFound_ShouldChangeQuantity() {
        Long newQuantity = 22L;
        OrderEntity order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        OrderItemEntity orderItem = createOrderItem(orderItemId);
        ProductEntity product = createProduct(true, 100L);
        order.addItem(orderItem);
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.of(product));
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(orderItem));
        when(orderItemRepository.save(any(OrderItemEntity.class))).thenAnswer(invoke -> invoke.getArgument(0));

        OrderItemResponse result = orderItemService.changeQuantity(orderId, orderItemId, newQuantity);

        verify(productRepository).findBySku(DEFAULT_SKU);
        verify(orderItemRepository).findById(orderItemId);
        verify(productRepository).save(product);
        verify(orderItemRepository).save(orderItem);
        assertEquals(orderItem.getOrder().getId(), result.orderId());
        assertEquals(newQuantity, result.quantity());
    }

    // 22 - 10 = 12 > 0 -> product stock should decrease have 100 - 12 = 88.
    @Test
    public void changeQuantity_WhenQuantityDifferenceIsPositive_ShouldDecreaseProductStock() {
        Long newQuantity = 22L;
        OrderEntity order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        OrderItemEntity orderItem = createOrderItem(orderItemId);
        ProductEntity product = createProduct(true, 100L);
        order.addItem(orderItem);
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.of(product));
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(orderItem));
        when(orderItemRepository.save(any(OrderItemEntity.class))).thenAnswer(invoke -> invoke.getArgument(0));

        OrderItemResponse result = orderItemService.changeQuantity(orderId, orderItemId, newQuantity);

        verify(productRepository).findBySku(DEFAULT_SKU);
        verify(orderItemRepository).findById(orderItemId);
        verify(productRepository).save(product);
        verify(orderItemRepository).save(orderItem);
        assertEquals(88L, product.getQuantity());
        assertEquals(orderItemId, result.id());
    }

    // 2 - 10 = -8 < 0 -> product stock should increase by 8, have 100 + 8 = 108.
    @Test
    public void changeQuantity_WhenQuantityDifferenceIsNegative_ShouldIncreaseProductStock() {
        Long newQuantity = 2L;
        OrderEntity order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        OrderItemEntity orderItem = createOrderItem(orderItemId);
        ProductEntity product = createProduct(true, 100L);
        order.addItem(orderItem);
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.of(product));
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(orderItem));
        when(orderItemRepository.save(any(OrderItemEntity.class))).thenAnswer(invoke -> invoke.getArgument(0));

        OrderItemResponse result = orderItemService.changeQuantity(orderId, orderItemId, newQuantity);

        verify(productRepository).findBySku(DEFAULT_SKU);
        verify(orderItemRepository).findById(orderItemId);
        verify(productRepository).save(product);
        verify(orderItemRepository).save(orderItem);
        assertEquals(108L, product.getQuantity());
        assertEquals(orderItemId, result.id());
    }

    // 10 - 10 = 0 -> product stock should not change, have 100.
    @Test
    public void changeQuantity_WhenQuantityDifferenceIsZero_ShouldNotChangeProductStock() {
        Long newQuantity = 10L;
        OrderEntity order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        OrderItemEntity orderItem = createOrderItem(orderItemId);
        ProductEntity product = createProduct(true, 100L);
        order.addItem(orderItem);
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.of(product));
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(orderItem));
        when(orderItemRepository.save(any(OrderItemEntity.class))).thenAnswer(invoke -> invoke.getArgument(0));

        OrderItemResponse result = orderItemService.changeQuantity(orderId, orderItemId, newQuantity);

        verify(productRepository).findBySku(DEFAULT_SKU);
        verify(orderItemRepository).findById(orderItemId);
        verify(productRepository, never()).save(any(ProductEntity.class));
        verify(orderItemRepository).save(orderItem);
        assertEquals(100L, product.getQuantity());
        assertEquals(orderItemId, result.id());
    }

    @Test
    public void changeQuantity_WhenProductNotFound_ShouldThrowProductNotFoundException() {
        Long newQuantity = 22L;
        OrderEntity order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        OrderItemEntity orderItem = createOrderItem(orderItemId);
        order.addItem(orderItem);
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(orderItem));
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.empty());

        ProductNotFoundException result = assertThrows(ProductNotFoundException.class,
                () -> orderItemService.changeQuantity(orderId, orderItemId, newQuantity));

        verify(orderItemRepository, never()).save(any(OrderItemEntity.class));
        assertEquals("Product with SKU sku could not be found.", result.getMessage());
    }

    @Test
    public void changeQuantity_WhenInsufficientProductStock_ShouldThrowInvalidProductException() {
        Long newQuantity = 200L;
        OrderEntity order = createOrder(orderId, OrderStatus.PENDING_PAYMENT);
        OrderItemEntity orderItem = createOrderItem(orderItemId);
        ProductEntity product = createProduct(true, 100L);
        order.addItem(orderItem);
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(orderItem));
        when(productRepository.findBySku(DEFAULT_SKU)).thenReturn(Optional.of(product));

        InsufficientStockException result = assertThrows(InsufficientStockException.class,
                () -> orderItemService.changeQuantity(orderId, orderItemId, newQuantity));

        verify(orderItemRepository, never()).save(any(OrderItemEntity.class));
        assertEquals("Insufficient stock.", result.getMessage());
    }

    @Test
    public void changeQuantity_WhenOrderItemNotFound_ShouldThrowOrderItemNotFoundException() {
        Long newQuantity = 22L;

        OrderItemNotFoundException result = assertThrows(OrderItemNotFoundException.class,
                () -> orderItemService.changeQuantity(orderId, orderItemId, newQuantity));

        verify(orderItemRepository, never()).save(any(OrderItemEntity.class));
        assertEquals(ORDER_ITEM_ERROR_MESSAGE, result.getMessage());
    }

    @Test
    public void changeQuantity_WhenOrderIdNotEqual_ShouldThrowOrderItemNotFoundException() {
        Long newQuantity = 22L;
        UUID differentOrderId = UUID.randomUUID();
        OrderEntity order = createOrder(differentOrderId, OrderStatus.PENDING_PAYMENT);
        OrderItemEntity orderItem = createOrderItem(orderItemId);
        order.addItem(orderItem);
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(orderItem));

        OrderItemNotFoundException result = assertThrows(OrderItemNotFoundException.class,
                () -> orderItemService.changeQuantity(orderId, orderItemId, newQuantity));

        verify(orderItemRepository, never()).save(any(OrderItemEntity.class));
        assertEquals(ORDER_ITEM_ERROR_MESSAGE, result.getMessage());
    }

    private static CreateOrderItemRequest createOrderItemRequestWithQuantity(Long quantity) {
        return new CreateOrderItemRequest(DEFAULT_SKU, quantity);
    }

    private static CreateOrderItemRequest createOrderItemRequest() {
        return new CreateOrderItemRequest(DEFAULT_SKU, DEFAULT_QUANTITY_LONG);
    }

    private static ProductEntity createProduct(boolean isActive, Long stock) {
        return ProductEntity.builder()
                .id(UUID.randomUUID())
                .quantity(stock)
                .name("product_name")
                .sku(DEFAULT_SKU)
                .description(DEFAULT_DESCRIPTION)
                .price(DEFAULT_UNIT_PRICE)
                .active(isActive)
                .build();
    }

    private static OrderItemEntity createOrderItem(UUID orderItemId) {
        return OrderItemEntity.builder()
                .id(orderItemId)
                .sku(DEFAULT_SKU)
                .description(DEFAULT_DESCRIPTION)
                .unitPrice(DEFAULT_UNIT_PRICE)
                .quantity(DEFAULT_QUANTITY_LONG)
                .build();

    }

    private static OrderEntity createOrder(UUID orderId, OrderStatus orderStatus) {
        return OrderEntity.builder()
                .id(orderId)
                .customerId(UUID.randomUUID())
                .currency(Currency.MXN)
                .status(orderStatus)
                .build();
    }

}
