package com.orders.messages.orders_demo.app.order.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerStatus;
import com.orders.messages.orders_demo.app.customer.internal.exceptions.CustomerNotFoundException;
import com.orders.messages.orders_demo.app.order.api.CreateOrderRequest;
import com.orders.messages.orders_demo.app.order.internal.exceptions.InvalidOrderStateException;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderAlreadyCancelledException;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderAlreadyExpiredException;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderAlreadyPaidException;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderNotFoundException;
import com.orders.messages.orders_demo.app.order_item.internal.OrderItemEntity;

@WebMvcTest(OrderController.class)
public class OrderControllerTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    private UUID customerId;
    private UUID orderId;

    private static final String DEFAULT_SKU = "sku";
    private static final String DEFAULT_DESCRIPTION = "description";
    private static final BigDecimal DEFAULT_UNIT_PRICE = new BigDecimal("123.00");
    private static final Long DEFAULT_QUANTITY = 1L;

    @BeforeEach
    public void setup() {
        customerId = UUID.randomUUID();
        orderId = UUID.randomUUID();
    }

    @Test
    public void getAllOrders_ShouldReturn200() throws Exception {
        OrderEntity order1 = createPendingOrder(customerId);
        OrderEntity order2 = createPendingOrder(customerId);
        when(orderService.getAllOrders()).thenReturn(List.of(order1, order2));

        mvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].customerId").value(customerId.toString()))
                .andExpect(jsonPath("$[0].currency").value("MXN"))
                .andExpect(jsonPath("$[0].amountTotal").value(0))
                .andExpect(jsonPath("$[0].status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$[1].customerId").value(customerId.toString()))
                .andExpect(jsonPath("$[1].currency").value("MXN"))
                .andExpect(jsonPath("$[1].amountTotal").value(0))
                .andExpect(jsonPath("$[1].status").value("PENDING_PAYMENT"));
        verify(orderService).getAllOrders();
    }

    @Test
    public void getOrder_ShouldReturn200() throws Exception {
        OrderEntity order = createPendingOrder(customerId);
        order.addItem(createOrderItem());
        when(orderService.getOrder(orderId)).thenReturn(order);

        mvc.perform(get("/api/v1/orders/{id}", orderId)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.currency").value("MXN"))
                .andExpect(jsonPath("$.amountTotal").value(123.00))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        verify(orderService).getOrder(orderId);
    }

    @Test
    void getOrder_WhenOrderNotFound_ShouldReturn404() throws Exception {
        when(orderService.getOrder(orderId))
                .thenThrow(new OrderNotFoundException("Order not found."));

        mvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Order not found."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId));
    }

    @Test
    public void createOrder_WhenRequestIsValid_ShouldReturn201() throws Exception {
        CreateOrderRequest request = createValidRequest(customerId);
        OrderEntity order = createPendingOrder(customerId);
        order.addItem(createOrderItem());
        when(orderService.createOrder(request)).thenReturn(order);

        mvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.currency").value("MXN"))
                .andExpect(jsonPath("$.amountTotal").value(123.00))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        verify(orderService).createOrder(request);
    }

    @Test
    public void createOrder_WhenCustomerDoesNotExist_ShouldReturn404() throws Exception {
        CreateOrderRequest request = createValidRequest(customerId);
        when(orderService.createOrder(request)).thenThrow(new CustomerNotFoundException());

        mvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Customer could not be found."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders"));
    }

    @Test
    public void createOrder_WhenValidationFailsWithNullCustomerId_ShouldReturn400() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .setCustomerId(null)
                .setCurrency(Currency.MXN)
                .build();

        mvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Customer id is required."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders"));
        verify(orderService, never()).createOrder(any());
    }

    @Test
    public void createOrder_WhenValidationFailsWithBlankCurrency_ShouldReturn400() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .setCustomerId(customerId)
                .setCurrency(null)
                .build();

        mvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Currency is required."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders"));
        verify(orderService, never()).createOrder(any());
    }

    @Test
    public void cancelOrder_WhenOrderIsOnPending_ShouldReturn200() throws Exception {
        OrderEntity order = createOrderWithStatus(customerId, OrderStatus.CANCELLED);
        order.addItem(createOrderItem());
        when(orderService.cancelOrder(orderId)).thenReturn(order);

        mvc.perform(patch("/api/v1/orders/{id}/cancel", orderId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.currency").value("MXN"))
                .andExpect(jsonPath("$.amountTotal").value(123.00))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        verify(orderService).cancelOrder(orderId);
    }

    @Test
    public void cancelOrder_WhenCustomerIdNotFound_ShouldReturn404() throws Exception {
        when(orderService.cancelOrder(orderId)).thenThrow(new OrderNotFoundException());

        mvc.perform(patch("/api/v1/orders/{id}/cancel", orderId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Order could not be found."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId + "/cancel"));
    }

    @ParameterizedTest
    @MethodSource("conflictExceptionsFromPending")
    public void cancelOrder_WhenOrderIsNotOnPending_ShouldReturn409(Exception exception, String message)
            throws Exception {
        when(orderService.cancelOrder(orderId)).thenThrow(exception);

        mvc.perform(patch("/api/v1/orders/{id}/cancel", orderId))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId + "/cancel"));
    }

    @Test
    public void refundOrder_WhenOrderIsValid_ShouldReturn200() throws Exception {
        OrderEntity order = createOrderWithStatus(customerId, OrderStatus.REFUNDED);
        order.addItem(createOrderItem());
        when(orderService.refundOrder(orderId)).thenReturn(order);

        mvc.perform(patch("/api/v1/orders/{id}/refund", orderId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.currency").value("MXN"))
                .andExpect(jsonPath("$.amountTotal").value(123.00))
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        verify(orderService).refundOrder(orderId);
    }

    @Test
    public void refundOrder_WithOrderNotFound_ShouldReturn404() throws Exception {
        when(orderService.refundOrder(orderId)).thenThrow(new OrderNotFoundException());

        mvc.perform(patch("/api/v1/orders/{id}/refund", orderId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Order could not be found."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId + "/refund"));
    }

    @ParameterizedTest
    @MethodSource("conflictExceptionsOnRefundOrder")
    public void refundOrder_WithOrderStateException_ShouldReturn409(Exception exception, String message)
            throws Exception {
        when(orderService.refundOrder(orderId)).thenThrow(exception);

        mvc.perform(patch("/api/v1/orders/{id}/refund", orderId))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId + "/refund"));
    }

    @Test
    public void expireOrder_WhenOrderIsValid_ShouldReturn200() throws Exception {
        OrderEntity order = createOrderWithStatus(customerId, OrderStatus.EXPIRED);
        order.addItem(createOrderItem());
        when(orderService.expireOrder(orderId)).thenReturn(order);

        mvc.perform(patch("/api/v1/orders/{id}/expire", orderId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.currency").value("MXN"))
                .andExpect(jsonPath("$.amountTotal").value(123.00))
                .andExpect(jsonPath("$.status").value("EXPIRED"));
        verify(orderService).expireOrder(orderId);
    }

    @Test
    public void expireOrder_WhenOrderIsNotFound_ShouldReturn404() throws Exception {
        when(orderService.expireOrder(orderId)).thenThrow(new OrderNotFoundException());

        mvc.perform(patch("/api/v1/orders/{id}/expire", orderId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Order could not be found."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId + "/expire"));
    }

    @ParameterizedTest
    @MethodSource("conflictExceptionsFromPending")
    public void expireOrder_WhenOrdesIsNotOnPending_ShouldReturn409(Exception exception, String message)
            throws Exception {
        when(orderService.expireOrder(orderId)).thenThrow(exception);

        mvc.perform(patch("/api/v1/orders/{id}/expire", orderId))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId + "/expire"));
    }

    private static CreateOrderRequest createValidRequest(UUID customerId) {
        return CreateOrderRequest.builder()
                .setCustomerId(customerId)
                .setCurrency(Currency.MXN)
                .build();
    }

    private static CustomerEntity createCustomer(UUID customerId) {
        return new CustomerEntity(customerId, "user_email", "user_name", CustomerStatus.ACTIVE);
    }

    private static OrderEntity createPendingOrder(UUID customerId) {
        return OrderEntity.builder()
                .customer(createCustomer(customerId))
                .currency(Currency.MXN)
                .build();
    }

    private static OrderEntity createOrderWithStatus(UUID customerId, OrderStatus status) {
        return OrderEntity.builder()
                .customer(createCustomer(customerId))
                .currency(Currency.MXN).status(status)
                .build();
    }

    private static OrderItemEntity createOrderItem() {
        return OrderItemEntity.builder()
                .sku(DEFAULT_SKU)
                .description(DEFAULT_DESCRIPTION)
                .unitPrice(DEFAULT_UNIT_PRICE)
                .quantity(DEFAULT_QUANTITY)
                .build();

    }

    @SuppressWarnings("unused")
    private static Stream<Arguments> conflictExceptionsFromPending() {
        return Stream.of(
                Arguments.of(new OrderAlreadyCancelledException(), "Order is already cancelled."),
                Arguments.of(new OrderAlreadyExpiredException(), "Expired orders cannot be modified."),
                Arguments.of(new OrderAlreadyPaidException(), "Paid orders cannot be modified."));
    }

    @SuppressWarnings("unused")
    private static Stream<Arguments> conflictExceptionsOnRefundOrder() {
        return Stream.of(
                Arguments.of(new OrderAlreadyCancelledException(), "Order is already cancelled."),
                Arguments.of(new InvalidOrderStateException("Only paid orders can be refunded."),
                        "Only paid orders can be refunded."),
                Arguments.of(new OrderAlreadyExpiredException(), "Expired orders cannot be modified."));
    }
}
