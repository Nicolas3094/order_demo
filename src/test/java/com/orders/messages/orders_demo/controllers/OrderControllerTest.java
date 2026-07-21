package com.orders.messages.orders_demo.controllers;

import java.math.BigDecimal;
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
import com.orders.messages.orders_demo.dtos.request.CreateOrderRequest;
import com.orders.messages.orders_demo.entity.Customer;
import com.orders.messages.orders_demo.entity.Order;
import com.orders.messages.orders_demo.enums.CustomerStatus;
import com.orders.messages.orders_demo.enums.OrderStatus;
import com.orders.messages.orders_demo.exceptions.customer.CustomerNotFoundException;
import com.orders.messages.orders_demo.exceptions.orders.InvalidOrderStateException;
import com.orders.messages.orders_demo.exceptions.orders.OrderAlreadyCancelledException;
import com.orders.messages.orders_demo.exceptions.orders.OrderAlreadyExpiredException;
import com.orders.messages.orders_demo.exceptions.orders.OrderAlreadyPaidException;
import com.orders.messages.orders_demo.exceptions.orders.OrderNotFoundException;
import com.orders.messages.orders_demo.services.OrderService;

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

    private static final BigDecimal DEFAULT_TOTAL_AMOUNT = new BigDecimal("123.00");
    private static final String DEFAULT_CURRENCY = "MXN";

    @BeforeEach
    public void setup() {
        customerId = UUID.randomUUID();
        orderId = UUID.randomUUID();
    }

    @Test
    public void getOrder_ShouldReturn200() throws Exception {
        Order order = createPendingOrder(customerId);
        when(orderService.getOrder(orderId)).thenReturn(order);

        mvc.perform(get("/api/v1/orders/{id}", orderId)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.currency").value(DEFAULT_CURRENCY))
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
        Order order = createPendingOrder(customerId);
        when(orderService.createOrder(request)).thenReturn(order);

        mvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.currency").value(DEFAULT_CURRENCY))
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
    public void createOrder_WhenValidationFailsWithNegativeAmountTotal_ShouldReturn400() throws Exception {
        BigDecimal amountTotal = new BigDecimal("-123.00");
        CreateOrderRequest request = CreateOrderRequest.builder()
                .setCustomerId(customerId)
                .setCurrency(DEFAULT_CURRENCY)
                .setAmountTotal(amountTotal)
                .build();

        mvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Amount must be greater than zero."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders"));
        verify(orderService, never()).createOrder(any());
    }

    @Test
    public void createOrder_WhenValidationFailsWithNullCustomerId_ShouldReturn400() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .setCurrency(DEFAULT_CURRENCY)
                .setAmountTotal(DEFAULT_TOTAL_AMOUNT)
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
        String currency = "";
        CreateOrderRequest request = CreateOrderRequest.builder()
                .setCustomerId(customerId)
                .setCurrency(currency)
                .setAmountTotal(DEFAULT_TOTAL_AMOUNT)
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
        Order order = createOrderWithStatus(customerId, OrderStatus.CANCELLED);
        when(orderService.cancelOrder(orderId)).thenReturn(order);

        mvc.perform(patch("/api/v1/orders/{id}/cancel", orderId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.currency").value(DEFAULT_CURRENCY))
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
    public void payOrder_WhenOrderWithCustomer_ShouldReturn200() throws Exception {
        Order order = createOrderWithStatus(customerId, OrderStatus.PAID);
        when(orderService.payOrder(orderId)).thenReturn(order);

        mvc.perform(patch("/api/v1/orders/{id}/pay", orderId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.currency").value(DEFAULT_CURRENCY))
                .andExpect(jsonPath("$.amountTotal").value(123.00))
                .andExpect(jsonPath("$.status").value("PAID"));
        verify(orderService).payOrder(orderId);
    }

    @Test
    public void payOrder_WhenOrderIsNotFound_ShouldReturn404() throws Exception {
        when(orderService.payOrder(orderId)).thenThrow(new OrderNotFoundException());

        mvc.perform(patch("/api/v1/orders/{id}/pay", orderId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Order could not be found."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId + "/pay"));
    }

    @ParameterizedTest
    @MethodSource("conflictExceptionsFromPending")
    public void payOrder_WhenOrdesIsNotOnPending_ShouldReturn409(Exception exception, String message) throws Exception {
        when(orderService.payOrder(orderId)).thenThrow(exception);

        mvc.perform(patch("/api/v1/orders/{id}/pay", orderId))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId + "/pay"));
    }

    @Test
    public void refundOrder_WhenOrderIsValid_ShouldReturn200() throws Exception {
        Order order = createOrderWithStatus(customerId, OrderStatus.REFUNDED);
        when(orderService.refundOrder(orderId)).thenReturn(order);

        mvc.perform(patch("/api/v1/orders/{id}/refund", orderId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.currency").value(DEFAULT_CURRENCY))
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
        Order order = createOrderWithStatus(customerId, OrderStatus.EXPIRED);
        when(orderService.expireOrder(orderId)).thenReturn(order);

        mvc.perform(patch("/api/v1/orders/{id}/expire", orderId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.currency").value(DEFAULT_CURRENCY))
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
                .setCurrency(DEFAULT_CURRENCY)
                .setAmountTotal(new BigDecimal("123.00"))
                .build();
    }

    private static Customer createCustomer(UUID customerId) {
        return new Customer(customerId, "user_email", "user_name", CustomerStatus.ACTIVE);
    }

    private static Order createPendingOrder(UUID customerId) {
        return new Order(createCustomer(customerId), DEFAULT_CURRENCY, DEFAULT_TOTAL_AMOUNT);
    }

    private static Order createOrderWithStatus(UUID customerId, OrderStatus status) {
        return new Order(createCustomer(customerId), DEFAULT_CURRENCY, DEFAULT_TOTAL_AMOUNT, status);
    }

    private static Stream<Arguments> conflictExceptionsFromPending() {
        return Stream.of(
                Arguments.of(
                        new OrderAlreadyCancelledException(),
                        "Order is already cancelled."),
                Arguments.of(
                        new OrderAlreadyExpiredException(),
                        "Expired orders cannot be modified."),
                Arguments.of(
                        new OrderAlreadyPaidException(),
                        "Paid orders cannot be modified."));
    }

    private static Stream<Arguments> conflictExceptionsOnRefundOrder() {
        return Stream.of(
                Arguments.of(
                        new OrderAlreadyCancelledException(),
                        "Order is already cancelled."),
                Arguments.of(
                        new InvalidOrderStateException("Only paid orders can be refunded."),
                        "Only paid orders can be refunded."),
                Arguments.of(
                        new OrderAlreadyExpiredException(),
                        "Expired orders cannot be modified."));
    }
}
