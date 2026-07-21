package com.orders.messages.orders_demo.controllers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.messages.orders_demo.dtos.request.CreateOrderRequest;
import com.orders.messages.orders_demo.entity.Customer;
import com.orders.messages.orders_demo.entity.Order;
import com.orders.messages.orders_demo.enums.CustomerStatus;
import com.orders.messages.orders_demo.exceptions.customer.CustomerNotFoundException;
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

    private static final String APPLICATION_JSON = "application/json";

    @Test
    public void getOrder_ShouldReturn200() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = new Order(
                new Customer(customerId, "user_email", "user_name", CustomerStatus.ACTIVE, Instant.now()),
                "MXN",
                new BigDecimal(123));
        when(orderService.getOrder(orderId)).thenReturn(order);

        mvc.perform(get("/api/v1/orders/{id}", orderId)).andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.currency").value("MXN"))
                .andExpect(jsonPath("$.amountTotal").value(123.00))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    @Test
    void getOrder_WhenOrderNotFound_ShouldReturn404() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrder(orderId))
                .thenThrow(new OrderNotFoundException("Order not found."));

        mvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Order not found."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId));
    }

    @Test
    public void createOrder_ShouldReturn201() throws Exception {
        UUID customerId = UUID.randomUUID();
        CreateOrderRequest request = CreateOrderRequest.builder()
                .setCustomerId(customerId)
                .setCurrency("MXN")
                .setAmountTotal(new BigDecimal(123))
                .build();
        Order order = new Order(
                new Customer(customerId, "user_email", "user_name", CustomerStatus.ACTIVE, Instant.now()),
                "MXN",
                new BigDecimal(123));
        when(orderService.createOrder(request)).thenReturn(order);

        mvc.perform(post("/api/v1/orders")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.currency").value("MXN"))
                .andExpect(jsonPath("$.amountTotal").value(123.00))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    @Test
    public void createOrder_ShouldReturn404_WhenCustomerDoesNotExist() throws Exception {
        UUID customerId = UUID.randomUUID();
        CreateOrderRequest request = CreateOrderRequest.builder()
                .setCustomerId(customerId)
                .setCurrency("MXN")
                .setAmountTotal(new BigDecimal(123))
                .build();
        when(orderService.createOrder(request)).thenThrow(new CustomerNotFoundException());

        mvc.perform(post("/api/v1/orders")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Customer could not be found."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders"));
    }

    @Test
    public void createOrder_WhenValidationFailsWihtNegativeAmountTotal_ShouldReturn400() throws Exception {
        UUID customerId = UUID.randomUUID();
        CreateOrderRequest request = CreateOrderRequest.builder()
                .setCustomerId(customerId)
                .setCurrency("MXN")
                .setAmountTotal(new BigDecimal(-123))
                .build();
        Order order = new Order(
                new Customer(customerId, "user_email", "user_name", CustomerStatus.ACTIVE, Instant.now()),
                "MXN",
                new BigDecimal(-123));
        when(orderService.createOrder(request)).thenReturn(order);

        mvc.perform(post("/api/v1/orders")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Amount must be greater than zero."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders"));
    }

    @Test
    public void createOrder_WhenValidationFailsWithNullCustomerId_ShouldReturn400() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .setCurrency("MXN")
                .setAmountTotal(new BigDecimal(123))
                .build();
        Order order = new Order(
                new Customer(null, "user_email", "user_name", CustomerStatus.ACTIVE, Instant.now()),
                "MXN",
                new BigDecimal(123));
        when(orderService.createOrder(request)).thenReturn(order);

        mvc.perform(post("/api/v1/orders")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Customer id is required."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders"));
    }

    @Test
    public void createOrder_WhenValidationFailsWithBlankCurrency_ShouldReturn400() throws Exception {
        UUID customerId = UUID.randomUUID();
        CreateOrderRequest request = CreateOrderRequest.builder()
                .setCustomerId(customerId)
                .setCurrency("")
                .setAmountTotal(new BigDecimal(123))
                .build();
        Order order = new Order(
                new Customer(customerId, "user_email", "user_name", CustomerStatus.ACTIVE, Instant.now()),
                "",
                new BigDecimal(123));
        when(orderService.createOrder(request)).thenReturn(order);

        mvc.perform(post("/api/v1/orders")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Currency is required."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders"));
    }

    @Test
    public void cancelOrder_ShouldReturn200() throws Exception {

    }

    @Test
    public void cancelOrder_ShouldReturn404() throws Exception {

    }

    @Test
    public void cancelOrder_ShouldReturn409() throws Exception {

    }

    @Test
    public void payOrder_ShouldReturn200() throws Exception {

    }

    @Test
    public void payOrder_ShouldReturn404() throws Exception {

    }

    @Test
    public void payOrder_ShouldReturn409() throws Exception {

    }

    @Test
    public void refundOrder_ShouldReturn200() throws Exception {

    }

    @Test
    public void refundOrder_ShouldReturn404() throws Exception {

    }

    @Test
    public void refundOrder_ShouldReturn409() throws Exception {

    }

    @Test
    public void expireOrder_ShouldReturn200() throws Exception {

    }

    @Test
    public void expireOrder_ShouldReturn404() throws Exception {

    }

    @Test
    public void expireOrder_ShouldReturn409() throws Exception {

    }

}
