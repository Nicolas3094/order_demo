package com.orders.messages.orders_demo.app.order.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.params.provider.EnumSource;
import com.orders.messages.orders_demo.app.order.internal.exceptions.InvalidOrderStateException;

import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.customer.api.CustomerInternalApi;
import com.orders.messages.orders_demo.app.customer.api.CustomerResponse;
import com.orders.messages.orders_demo.app.customer.internal.CustomerStatus;
import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerNotFoundException;
import com.orders.messages.orders_demo.app.order.api.CreateOrderRequest;
import com.orders.messages.orders_demo.app.order.api.OrderResponse;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderAlreadyCancelledException;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderAlreadyExpiredException;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderAlreadyPaidException;
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

    @Mock
    private JdbcTemplate jdbcTemplate;

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

    @ParameterizedTest
    @MethodSource("invalidStatesForCancel")
    public void cancelOrder_WhenOrderCannotBeCancelled_ShouldThrowException(
            OrderStatus status, Class<? extends Exception> exceptionType, String message) {
        fakeOrder = createOrder(status);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));

        Exception result = assertThrows(exceptionType,
                () -> orderService.cancelOrder(orderId));

        verify(productInternalApi, never()).increaceProductStock(anyString(), anyLong());
        verify(orderRepository, never()).save(any(OrderEntity.class));
        assertEquals(message, result.getMessage());
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

    @ParameterizedTest
    @MethodSource("invalidStatesForExpire")
    public void expireOrder_WhenOrderCannotBeExpired_ShouldThrowException(
            OrderStatus status, Class<? extends Exception> exceptionType, String message) {
        fakeOrder = createOrder(status);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));

        Exception result = assertThrows(exceptionType,
                () -> orderService.expireOrder(orderId));

        verify(productInternalApi, never()).increaceProductStock(anyString(), anyLong());
        verify(orderRepository, never()).save(any(OrderEntity.class));
        assertEquals(message, result.getMessage());
    }
    /*
     * 
     * refundOrder
     * 
     */

    @Test
    public void refundOrder_WhenOrderExists_ShouldRefundOrder() {
        fakeOrder = buildOrder(OrderStatus.PAID);
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
        fakeOrder = buildOrder(OrderStatus.PAID);
        fakeOrder.addItem(item1);
        fakeOrder.addItem(item2);
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
        fakeOrder = buildOrder(OrderStatus.PAID);
        fakeOrder.addItem(item1);
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

    /*
     * 
     * paymentInProgress
     * 
     */

    @Test
    public void paymentInProgress_WhenOrderExists_ShouldSetStatusAsPaymentInProgress() {
        fakeOrder.addItem(createOrderItem("SKU-123", 2L));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(fakeOrder));
        when(orderRepository.save(fakeOrder)).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse result = orderService.paymentInProgress(orderId);

        assertEquals(OrderStatus.PAYMENT_IN_PROGRESS, result.status());
        assertThat(new BigDecimal("20")).isEqualTo(fakeOrder.getAmountTotal());
        assertThat(fakeOrder.getItems()).hasSize(1);
        assertThat(fakeOrder.getItems()).extracting(OrderItemEntity::getQuantity).contains(2L);
        verify(orderRepository).save(fakeOrder);
        verifyNoInteractions(productInternalApi);
    }

    @Test
    public void paymentInProgress_WhenOrderNotFound_ShouldThrowOrderNotFoundException() {
        Exception result = assertThrows(OrderNotFoundException.class, () -> orderService.paymentInProgress(orderId));

        assertEquals("Order could not be found.", result.getMessage());
        verify(orderRepository, never()).save(any(OrderEntity.class));
    }

    @Test
    void createOrReuseOrder_WhenNoOpenOrderExists_ShouldCreateOrder() {
        UUID customerId = UUID.randomUUID();
        var request = openOrderRequest(customerId, Currency.MXN);
        mockExistingCustomer(customerId);
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = orderService.createOrReuseOrder(request);

        assertThat(result.created()).isTrue();
        assertThat(result.order().customerId()).isEqualTo(customerId);
        assertThat(result.order().status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(result.order().currency()).isEqualTo(Currency.MXN);
        assertThat(result.order().amountTotal()).isEqualByComparingTo("0");
        verify(orderRepository).save(any(OrderEntity.class));
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = { "PENDING_PAYMENT", "PAYMENT_IN_PROGRESS" })
    void createOrReuseOrder_WhenOpenOrderExists_ShouldReturnItWithoutSaving(OrderStatus status) {
        UUID customerId = UUID.randomUUID();
        var existing = OrderEntity.builder().id(UUID.randomUUID()).customerId(customerId)
                .currency(Currency.MXN).status(status).addItem(createOrderItem("SKU-123", 2L)).build();
        mockExistingCustomer(customerId);
        when(orderRepository.findByCustomerIdAndStatusIn(customerId,
                List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_IN_PROGRESS)))
                .thenReturn(Optional.of(existing));

        var result = orderService.createOrReuseOrder(openOrderRequest(customerId, Currency.MXN));

        assertThat(result.created()).isFalse();
        assertThat(result.order().id()).isEqualTo(existing.getId());
        assertThat(result.order().status()).isEqualTo(status);
        assertThat(result.order().amountTotal()).isEqualByComparingTo("20");
        assertThat(existing.getItems()).hasSize(1);
        assertThat(existing.getItems().get(0).getQuantity()).isEqualTo(2L);
        verify(orderRepository, never()).save(any(OrderEntity.class));
        verifyNoInteractions(productInternalApi);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = { "PENDING_PAYMENT", "PAYMENT_IN_PROGRESS" })
    void createOrReuseOrder_WhenCurrencyDiffers_ShouldRejectWithoutChangingOpenOrder(OrderStatus status) {
        UUID customerId = UUID.randomUUID();
        var existing = OrderEntity.builder().id(UUID.randomUUID()).customerId(customerId)
                .currency(Currency.MXN).status(status).build();
        mockExistingCustomer(customerId);
        when(orderRepository.findByCustomerIdAndStatusIn(customerId,
                List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_IN_PROGRESS)))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> orderService.createOrReuseOrder(openOrderRequest(customerId, Currency.USD)))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessage("The open order uses a different currency.");

        assertThat(existing.getCurrency()).isEqualTo(Currency.MXN);
        assertThat(existing.getStatus()).isEqualTo(status);
        verify(orderRepository, never()).save(any(OrderEntity.class));
    }

    @Test
    void createOrReuseOrder_WhenCustomerIsMissing_ShouldRejectBeforeQueryingOrders() {
        UUID customerId = UUID.randomUUID();
        when(customerInternalApi.getCustomer(customerId)).thenThrow(new CustomerNotFoundException());

        assertThatThrownBy(() -> orderService.createOrReuseOrder(openOrderRequest(customerId, Currency.MXN)))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessage("Customer could not be found.");

        verifyNoInteractions(orderRepository, jdbcTemplate);
    }

    @Test
    void createOrReuseOrder_WhenCustomerIsBlocked_ShouldRejectBeforeQueryingOrders() {
        UUID customerId = UUID.randomUUID();
        when(customerInternalApi.getCustomer(customerId)).thenReturn(CustomerResponse.builder()
                .id(customerId).status(CustomerStatus.BLOCKED).build());

        assertThatThrownBy(() -> orderService.createOrReuseOrder(openOrderRequest(customerId, Currency.MXN)))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessage("Blocked customers cannot create or reuse orders.");

        verifyNoInteractions(orderRepository, jdbcTemplate, productInternalApi);
    }

    private void mockExistingCustomer(UUID customerId) {
        when(customerInternalApi.getCustomer(customerId)).thenReturn(CustomerResponse.builder()
                .id(customerId).email("customer@example.com").name("Test Customer")
                .status(CustomerStatus.ACTIVE).build());
    }

    private CreateOrderRequest openOrderRequest(UUID customerId, Currency currency) {
        return CreateOrderRequest.builder().customerId(customerId).currency(currency).build();
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

    private static Stream<Arguments> invalidStatesForCancel() {
        return Stream.of(
                Arguments.of(
                        OrderStatus.CANCELLED,
                        OrderAlreadyCancelledException.class,
                        "Order is already cancelled."),
                Arguments.of(
                        OrderStatus.EXPIRED,
                        OrderAlreadyExpiredException.class,
                        "Expired orders cannot be modified."),
                Arguments.of(
                        OrderStatus.PAID,
                        OrderAlreadyPaidException.class,
                        "Paid orders cannot be modified."));
    }

    private static Stream<Arguments> invalidStatesForExpire() {
        return Stream.of(
                Arguments.of(
                        OrderStatus.CANCELLED,
                        OrderAlreadyCancelledException.class,
                        "Order is already cancelled."),
                Arguments.of(
                        OrderStatus.EXPIRED,
                        OrderAlreadyExpiredException.class,
                        "Expired orders cannot be modified."),
                Arguments.of(
                        OrderStatus.PAID,
                        OrderAlreadyPaidException.class,
                        "Paid orders cannot be modified."));
    }

    private static OrderEntity buildOrder(OrderStatus status) {
        return OrderEntity.builder()
                .id(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .status(status)
                .build();
    }
}
