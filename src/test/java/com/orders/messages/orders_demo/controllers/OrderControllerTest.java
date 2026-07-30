package com.orders.messages.orders_demo.controllers;

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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.messages.orders_demo.dtos.request.CreateOrderItemRequest;
import com.orders.messages.orders_demo.dtos.request.CreateOrderRequest;
import com.orders.messages.orders_demo.dtos.request.CreatePaymentAttemptRequest;
import com.orders.messages.orders_demo.dtos.request.OrderItemChangeQuantityRequest;
import com.orders.messages.orders_demo.dtos.request.OrderItemChangeUnitPriceRequest;
import com.orders.messages.orders_demo.dtos.request.PaymentFailedRequest;
import com.orders.messages.orders_demo.dtos.request.PaymentSucceededRequest;
import com.orders.messages.orders_demo.entity.Customer;
import com.orders.messages.orders_demo.entity.Order;
import com.orders.messages.orders_demo.entity.OrderItem;
import com.orders.messages.orders_demo.entity.PaymentAttempt;
import com.orders.messages.orders_demo.enums.Currency;
import com.orders.messages.orders_demo.enums.CustomerStatus;
import com.orders.messages.orders_demo.enums.OrderStatus;
import com.orders.messages.orders_demo.enums.PaymentProvider;
import com.orders.messages.orders_demo.enums.PaymentStatus;
import com.orders.messages.orders_demo.exceptions.customer.CustomerNotFoundException;
import com.orders.messages.orders_demo.exceptions.order_item.InvalidOrderItemStateException;
import com.orders.messages.orders_demo.exceptions.order_item.OrderItemNotFoundException;
import com.orders.messages.orders_demo.exceptions.orders.InvalidOrderStateException;
import com.orders.messages.orders_demo.exceptions.orders.OrderAlreadyCancelledException;
import com.orders.messages.orders_demo.exceptions.orders.OrderAlreadyExpiredException;
import com.orders.messages.orders_demo.exceptions.orders.OrderAlreadyPaidException;
import com.orders.messages.orders_demo.exceptions.orders.OrderNotFoundException;
import com.orders.messages.orders_demo.exceptions.payment.PaymentNotFoundException;
import com.orders.messages.orders_demo.services.OrderItemService;
import com.orders.messages.orders_demo.services.OrderService;
import com.orders.messages.orders_demo.services.PaymentAttemptService;

@WebMvcTest(OrderController.class)
public class OrderControllerTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;
    @MockitoBean
    private PaymentAttemptService paymentAttemptService;
    @MockitoBean
    private OrderItemService orderItemService;

    private UUID customerId;
    private UUID orderId;
    private UUID orderItemId;
    private UUID paymentId;

    private static final String DEFAULT_IDEMPOTENCY_KEY = "idempotency_key";
    private static final String DEFAULT_PROVIDER_REF = "provider_ref";
    private static final String DEFAULT_SKU = "sku";
    private static final String DEFAULT_DESCRIPTION = "description";
    private static final BigDecimal DEFAULT_UNIT_PRICE = new BigDecimal("123.00");
    private static final Long DEFAULT_QUANTITY = 1L;

    @BeforeEach
    public void setup() {
        customerId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
        orderItemId = UUID.randomUUID();
    }

    /*
     * 
     * ORDER
     * 
     */

    @Test
    public void getAllOrders_ShouldReturn200() throws Exception {
        Order order1 = createPendingOrder(customerId);
        Order order2 = createPendingOrder(customerId);
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
        Order order = createPendingOrder(customerId);
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
        Order order = createPendingOrder(customerId);
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
        Order order = createOrderWithStatus(customerId, OrderStatus.CANCELLED);
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
        Order order = createOrderWithStatus(customerId, OrderStatus.REFUNDED);
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
        Order order = createOrderWithStatus(customerId, OrderStatus.EXPIRED);
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

    /*
     * 
     * ORDER ITEM
     * 
     */

    @Test
    public void getAllOrderItems_ShouldReturn200() throws Exception {
        OrderItem orderItem1 = createOrderItem();
        OrderItem orderItem2 = createOrderItem();
        when(orderItemService.getAllOrderItems(orderId))
                .thenReturn(List.of(orderItem1, orderItem2));

        mvc.perform(get("/api/v1/orders/{orderId}/items", orderId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sku").value(DEFAULT_SKU))
                .andExpect(jsonPath("$[0].description").value(DEFAULT_DESCRIPTION))
                .andExpect(jsonPath("$[0].unitPrice").value(123.00))
                .andExpect(jsonPath("$[0].quantity").value(DEFAULT_QUANTITY))
                .andExpect(jsonPath("$[1].sku").value(DEFAULT_SKU))
                .andExpect(jsonPath("$[1].description").value(DEFAULT_DESCRIPTION))
                .andExpect(jsonPath("$[1].unitPrice").value(123.00))
                .andExpect(jsonPath("$[1].quantity").value(DEFAULT_QUANTITY));
        verify(orderItemService).getAllOrderItems(orderId);
    }

    @Test
    public void getAllOrderItems_WhenOrderNotFound_ShouldReturn404() throws Exception {
        when(orderItemService.getAllOrderItems(orderId)).thenThrow(new OrderNotFoundException());

        mvc.perform(get("/api/v1/orders/{orderId}/items", orderId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Order could not be found."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId + "/items"));
        verify(orderItemService).getAllOrderItems(orderId);
    }

    @Test
    public void getOrderItem_ShouldReturn200() throws Exception {
        Order order = createPendingOrder(customerId);
        OrderItem orderItem = createOrderItem();
        order.addItem(orderItem);

        when(orderItemService.getOrderItem(orderId, orderItemId))
                .thenReturn(orderItem);

        mvc.perform(get("/api/v1/orders/{orderId}/items/{orderItemId}", orderId, orderItemId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sku").value(DEFAULT_SKU))
                .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
                .andExpect(jsonPath("$.unitPrice").value(123.00))
                .andExpect(jsonPath("$.quantity").value(DEFAULT_QUANTITY));
        verify(orderItemService).getOrderItem(orderId, orderItemId);
    }

    @Test
    public void getOrderItem_WhenOrderItemNotFound_ShouldReturn404() throws Exception {
        when(orderItemService.getOrderItem(orderId, orderItemId)).thenThrow(new OrderItemNotFoundException());

        mvc.perform(get("/api/v1/orders/{orderId}/items/{orderItemId}", orderId, orderItemId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Order item not found."))
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/orders/" + orderId + "/items/" + orderItemId));
        verify(orderItemService).getOrderItem(orderId, orderItemId);
    }

    @Test
    public void createOrderItem_WhenRequestIsValid_ShouldReturn201() throws Exception {
        CreateOrderItemRequest request = createOrderItemRequest();
        Order order = createPendingOrder(customerId);
        OrderItem orderItem = createOrderItem();
        order.addItem(orderItem);
        when(orderItemService.createOrderItem(orderId, request)).thenReturn(orderItem);

        mvc.perform(post("/api/v1/orders/{orderId}/items", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sku").value(DEFAULT_SKU))
                .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
                .andExpect(jsonPath("$.unitPrice").value(123.00))
                .andExpect(jsonPath("$.quantity").value(DEFAULT_QUANTITY));
        verify(orderItemService).createOrderItem(orderId, request);
    }

    @Test
    public void createOrderItem_WhenOrderNotFound_ShouldReturn404() throws Exception {
        CreateOrderItemRequest request = createOrderItemRequest();
        when(orderItemService.createOrderItem(orderId, request)).thenThrow(new OrderNotFoundException());

        mvc.perform(post("/api/v1/orders/{orderId}/items", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Order could not be found."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId + "/items"));
    }

    @Test
    public void createOrderItem_WhenOrderCannotReceiveItems_ShouldReturn409() throws Exception {
        CreateOrderItemRequest request = createOrderItemRequest();
        when(orderItemService.createOrderItem(orderId, request))
                .thenThrow(new InvalidOrderItemStateException(
                        "Only pending orders can receive items."));

        mvc.perform(post("/api/v1/orders/{orderId}/items", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Only pending orders can receive items."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId + "/items"));
    }

    @Test
    public void createOrderItem_WhenValidationFailsWithBlankSku_ShouldReturn400() throws Exception {
        CreateOrderItemRequest request = new CreateOrderItemRequest("", DEFAULT_QUANTITY);

        mvc.perform(post("/api/v1/orders/{orderId}/items", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Order item must have SKU."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId + "/items"));
        verify(orderItemService, never()).createOrderItem(any(), any());
    }

    @Test
    public void createOrderItem_WhenValidationFailsWithNegativeQuantity_ShouldReturn400() throws Exception {
        CreateOrderItemRequest request = new CreateOrderItemRequest(DEFAULT_SKU, -5L);

        mvc.perform(post("/api/v1/orders/{orderId}/items", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Order item quantity must be positive."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId + "/items"));
        verify(orderItemService, never()).createOrderItem(any(UUID.class), any(CreateOrderItemRequest.class));
    }

    @Test
    public void deleteOrderItem_ShouldReturn204() throws Exception {
        mvc.perform(delete("/api/v1/orders/{orderId}/items/{orderItemId}", orderId, orderItemId))
                .andExpect(status().isNoContent());

        verify(orderItemService).deleteOrderItem(orderId, orderItemId);
    }

    @Test
    public void deleteOrderItem_WhenOrderItemNotFound_ShouldReturn404() throws Exception {
        doThrow(new OrderItemNotFoundException())
                .when(orderItemService)
                .deleteOrderItem(orderId, orderItemId);

        mvc.perform(delete("/api/v1/orders/{orderId}/items/{orderItemId}",
                orderId, orderItemId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Order item not found."))
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/orders/" + orderId + "/items/" + orderItemId));
        verify(orderItemService).deleteOrderItem(orderId, orderItemId);
    }

    @Test
    public void deleteOrderItem_WhenOrderCannotModifyItems_ShouldReturn409() throws Exception {
        doThrow(new InvalidOrderItemStateException(
                "Only pending orders can modify items."))
                .when(orderItemService)
                .deleteOrderItem(orderId, orderItemId);

        mvc.perform(delete("/api/v1/orders/{orderId}/items/{orderItemId}", orderId, orderItemId))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Only pending orders can modify items."))
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/orders/" + orderId + "/items/" + orderItemId));
        verify(orderItemService).deleteOrderItem(orderId, orderItemId);
    }

    @Test
    public void changeOrderItemUnitPrice_ShouldReturn200() throws Exception {
        BigDecimal newUnitPrice = new BigDecimal("8.00");
        OrderItemChangeUnitPriceRequest request = new OrderItemChangeUnitPriceRequest(newUnitPrice);
        Order order = createPendingOrder(customerId);
        OrderItem orderItem = createOrderItem();
        order.addItem(orderItem);
        orderItem.changeUnitPrice(newUnitPrice);
        when(orderItemService.changeUnitPrice(orderId, orderItemId, newUnitPrice))
                .thenReturn(orderItem);

        mvc.perform(patch("/api/v1/orders/{orderId}/items/{orderItemId}/price",
                orderId, orderItemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sku").value(DEFAULT_SKU))
                .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
                .andExpect(jsonPath("$.unitPrice").value(8.00))
                .andExpect(jsonPath("$.quantity").value(DEFAULT_QUANTITY));
        verify(orderItemService).changeUnitPrice(orderId, orderItemId, newUnitPrice);
    }

    @Test
    public void changeOrderItemUnitPrice_WhenOrderItemNotFound_ShouldReturn404() throws Exception {
        BigDecimal newUnitPrice = new BigDecimal("8.00");
        OrderItemChangeUnitPriceRequest request = new OrderItemChangeUnitPriceRequest(newUnitPrice);
        when(orderItemService.changeUnitPrice(orderId, orderItemId, newUnitPrice))
                .thenThrow(new OrderItemNotFoundException());

        mvc.perform(patch("/api/v1/orders/{orderId}/items/{orderItemId}/price", orderId, orderItemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Order item not found."))
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/orders/" + orderId + "/items/" + orderItemId
                                + "/price"));
        verify(orderItemService).changeUnitPrice(orderId, orderItemId, newUnitPrice);
    }

    @Test
    public void changeOrderItemUnitPrice_WhenOrderCannotModifyItems_ShouldReturn409() throws Exception {
        BigDecimal newUnitPrice = new BigDecimal("8.00");
        OrderItemChangeUnitPriceRequest request = new OrderItemChangeUnitPriceRequest(newUnitPrice);
        when(orderItemService.changeUnitPrice(orderId, orderItemId, newUnitPrice))
                .thenThrow(new InvalidOrderItemStateException(
                        "Only pending orders can modify items."));

        mvc.perform(patch("/api/v1/orders/{orderId}/items/{orderItemId}/price", orderId, orderItemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Only pending orders can modify items."))
                .andExpect(jsonPath("$.path").value(
                        "/api/v1/orders/" + orderId + "/items/" + orderItemId + "/price"));
        verify(orderItemService).changeUnitPrice(orderId, orderItemId, newUnitPrice);
    }

    @Test
    public void changeOrderItemUnitPrice_WhenUnitPriceIsNegative_ShouldReturn400() throws Exception {
        OrderItemChangeUnitPriceRequest request = new OrderItemChangeUnitPriceRequest(new BigDecimal("-8.00"));
        mvc.perform(patch("/api/v1/orders/{orderId}/items/{orderItemId}/price", orderId, orderItemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
        verify(orderItemService, never()).changeUnitPrice(any(), any(), any());
    }

    @Test
    public void changeOrderItemQuantity_ShouldReturn200() throws Exception {
        Long newQuantity = 20L;
        OrderItemChangeQuantityRequest request = new OrderItemChangeQuantityRequest(newQuantity);
        Order order = createPendingOrder(customerId);
        OrderItem orderItem = createOrderItem();
        order.addItem(orderItem);
        orderItem.changeQuantity(newQuantity);
        when(orderItemService.changeQuantity(orderId, orderItemId, newQuantity)).thenReturn(orderItem);

        mvc.perform(patch("/api/v1/orders/{orderId}/items/{orderItemId}/quantity", orderId, orderItemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sku").value(DEFAULT_SKU))
                .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
                .andExpect(jsonPath("$.unitPrice").value(123.00))
                .andExpect(jsonPath("$.quantity").value(newQuantity));
        verify(orderItemService).changeQuantity(orderId, orderItemId, newQuantity);
    }

    @Test
    public void changeOrderItemQuantity_WhenOrderItemNotFound_ShouldReturn404() throws Exception {
        Long newQuantity = 20L;
        OrderItemChangeQuantityRequest request = new OrderItemChangeQuantityRequest(newQuantity);
        when(orderItemService.changeQuantity(orderId, orderItemId, newQuantity))
                .thenThrow(new OrderItemNotFoundException());

        mvc.perform(patch("/api/v1/orders/{orderId}/items/{orderItemId}/quantity", orderId, orderItemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Order item not found."))
                .andExpect(
                        jsonPath("$.path").value("/api/v1/orders/" + orderId + "/items/"
                                + orderItemId + "/quantity"));
        verify(orderItemService).changeQuantity(orderId, orderItemId, newQuantity);
    }

    @Test
    public void changeOrderItemQuantity_WhenOrderCannotModifyItems_ShouldReturn409() throws Exception {
        Long newQuantity = 20L;
        OrderItemChangeQuantityRequest request = new OrderItemChangeQuantityRequest(newQuantity);
        when(orderItemService.changeQuantity(orderId, orderItemId, newQuantity))
                .thenThrow(new InvalidOrderItemStateException("Only pending orders can modify items."));

        mvc.perform(patch("/api/v1/orders/{orderId}/items/{orderItemId}/quantity", orderId, orderItemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Only pending orders can modify items."))
                .andExpect(
                        jsonPath("$.path").value("/api/v1/orders/" + orderId + "/items/"
                                + orderItemId + "/quantity"));
        verify(orderItemService).changeQuantity(orderId, orderItemId, newQuantity);
    }

    @Test
    public void changeOrderItemQuantity_WhenQuantityIsNegative_ShouldReturn400() throws Exception {
        OrderItemChangeQuantityRequest request = new OrderItemChangeQuantityRequest(-5L);

        mvc.perform(patch("/api/v1/orders/{orderId}/items/{orderItemId}/quantity",
                orderId, orderItemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
        verify(orderItemService, never()).changeQuantity(any(), any(), any());
    }

    /*
     * 
     * PAYMENT ATTEMPT
     * 
     */

    @Test
    public void getPayment_ShouldReturn200() throws Exception {
        Order order = createPendingOrder(customerId);
        PaymentAttempt paymentAttempt = createPaymentAttempt(paymentId, order);
        when(paymentAttemptService.getPaymentAttempt(orderId, paymentId)).thenReturn(paymentAttempt);

        mvc.perform(get("/api/v1/orders/{orderId}/payments/{paymentId}", orderId, paymentId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.provider").value("NONE"))
                .andExpect(jsonPath("$.idempotencyKey").value(DEFAULT_IDEMPOTENCY_KEY))
                .andExpect(jsonPath("$.status").value("CREATED"));
        verify(paymentAttemptService).getPaymentAttempt(orderId, paymentId);
    }

    @Test
    void getPayment_WhenPaymentNotFound_ShouldReturn404() throws Exception {
        when(paymentAttemptService.getPaymentAttempt(orderId, paymentId))
                .thenThrow(new PaymentNotFoundException());

        mvc.perform(get("/api/v1/orders/{orderId}/payments/{paymentId}", orderId, paymentId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Payment attempt not found."))
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/orders/" + orderId + "/payments/" + paymentId));
    }

    @Test
    public void createPayment_WhenRequestIsValid_ShouldReturn201() throws Exception {
        CreatePaymentAttemptRequest request = new CreatePaymentAttemptRequest(
                PaymentProvider.NONE, DEFAULT_IDEMPOTENCY_KEY);
        Order order = createPendingOrder(customerId);
        PaymentAttempt paymentAttempt = createPaymentAttempt(paymentId, order);
        when(paymentAttemptService.createPaymentAttempt(orderId, request)).thenReturn(paymentAttempt);

        mvc.perform(post("/api/v1/orders/{orderId}/payments", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.provider").value("NONE"))
                .andExpect(jsonPath("$.idempotencyKey").value(DEFAULT_IDEMPOTENCY_KEY))
                .andExpect(jsonPath("$.status").value("CREATED"));
        verify(paymentAttemptService).createPaymentAttempt(orderId, request);
    }

    @Test
    void createPayment_WhenPaymentNotFound_ShouldReturn404() throws Exception {
        CreatePaymentAttemptRequest request = new CreatePaymentAttemptRequest(
                PaymentProvider.NONE, DEFAULT_IDEMPOTENCY_KEY);
        when(paymentAttemptService.createPaymentAttempt(orderId, request))
                .thenThrow(new PaymentNotFoundException());

        mvc.perform(post("/api/v1/orders/{orderId}/payments", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Payment attempt not found."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId + "/payments"));
    }

    @Test
    public void createPayment_WhenValidationFailsWithEmptyProvider_ShouldReturn400() throws Exception {
        CreatePaymentAttemptRequest request = new CreatePaymentAttemptRequest(
                null, DEFAULT_IDEMPOTENCY_KEY);
        Order order = createPendingOrder(customerId);
        PaymentAttempt paymentAttempt = createPaymentAttempt(paymentId, order);
        when(paymentAttemptService.createPaymentAttempt(orderId, request)).thenReturn(paymentAttempt);

        mvc.perform(post("/api/v1/orders/{orderId}/payments", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Payment must have provider."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId + "/payments"));
    }

    @Test
    public void createPayment_WhenValidationFailsWithEmptyIdempotencyKey_ShouldReturn400() throws Exception {
        CreatePaymentAttemptRequest request = new CreatePaymentAttemptRequest(
                PaymentProvider.NONE, null);
        Order order = createPendingOrder(customerId);
        PaymentAttempt paymentAttempt = createPaymentAttempt(paymentId, order);
        when(paymentAttemptService.createPaymentAttempt(orderId, request)).thenReturn(paymentAttempt);

        mvc.perform(post("/api/v1/orders/{orderId}/payments", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Payment must have idempotency key."))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/" + orderId + "/payments"));
    }

    @Test
    public void startProcessing_ShouldReturn200() throws Exception {
        Order order = createOrderWithStatus(customerId, OrderStatus.PENDING_PAYMENT);
        PaymentAttempt paymentAttempt = createPaymentAttemptWithStatus(paymentId, order,
                PaymentStatus.PROCESSING);
        when(paymentAttemptService.startProcessing(orderId, paymentId)).thenReturn(paymentAttempt);

        mvc.perform(patch("/api/v1/orders/{orderId}/payments/{paymentId}/processing", orderId, paymentId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.provider").value("NONE"))
                .andExpect(jsonPath("$.idempotencyKey").value(DEFAULT_IDEMPOTENCY_KEY))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
        verify(paymentAttemptService).startProcessing(orderId, paymentId);
    }

    @Test
    public void markPaymentAsSucceeded_ShouldReturn200() throws Exception {
        PaymentSucceededRequest paymentSucceededRequest = new PaymentSucceededRequest(DEFAULT_PROVIDER_REF);
        Order order = createOrderWithStatus(customerId, OrderStatus.PAID);
        PaymentAttempt paymentAttempt = createPaymentAttemptWithStatus(paymentId, order,
                PaymentStatus.SUCCEEDED);
        when(paymentAttemptService.markAsSucceeded(orderId, paymentId, DEFAULT_PROVIDER_REF))
                .thenReturn(paymentAttempt);

        mvc.perform(patch("/api/v1/orders/{orderId}/payments/{paymentId}/succeeded", orderId, paymentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentSucceededRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.provider").value("NONE"))
                .andExpect(jsonPath("$.idempotencyKey").value(DEFAULT_IDEMPOTENCY_KEY))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        verify(paymentAttemptService).markAsSucceeded(orderId, paymentId, DEFAULT_PROVIDER_REF);
    }

    @Test
    public void markPaymentAsFailed_ShouldReturn200() throws Exception {
        PaymentFailedRequest paymentFailedRequest = new PaymentFailedRequest(500, "Internal error.");
        Order order = createOrderWithStatus(customerId, OrderStatus.PENDING_PAYMENT);
        PaymentAttempt paymentAttempt = createPaymentAttemptWithStatus(paymentId, order, PaymentStatus.FAILED);
        when(paymentAttemptService.markAsFailed(orderId, paymentId, 500, "Internal error."))
                .thenReturn(paymentAttempt);

        mvc.perform(patch("/api/v1/orders/{orderId}/payments/{paymentId}/failed", orderId, paymentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentFailedRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.provider").value("NONE"))
                .andExpect(jsonPath("$.idempotencyKey").value(DEFAULT_IDEMPOTENCY_KEY))
                .andExpect(jsonPath("$.status").value("FAILED"));
        verify(paymentAttemptService).markAsFailed(orderId, paymentId, 500, "Internal error.");
    }

    @Test
    public void markPaymentAsCancelled_ShouldReturn200() throws Exception {
        Order order = createOrderWithStatus(customerId, OrderStatus.PENDING_PAYMENT);
        PaymentAttempt paymentAttempt = createPaymentAttemptWithStatus(paymentId, order,
                PaymentStatus.CANCELLED);
        when(paymentAttemptService.markAsCancelled(orderId, paymentId)).thenReturn(paymentAttempt);

        mvc.perform(patch("/api/v1/orders/{orderId}/payments/{paymentId}/cancel", orderId, paymentId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.provider").value("NONE"))
                .andExpect(jsonPath("$.idempotencyKey").value(DEFAULT_IDEMPOTENCY_KEY))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        verify(paymentAttemptService).markAsCancelled(orderId, paymentId);
    }

    private static CreateOrderRequest createValidRequest(UUID customerId) {
        return CreateOrderRequest.builder()
                .setCustomerId(customerId)
                .setCurrency(Currency.MXN)
                .build();
    }

    private static PaymentAttempt createPaymentAttempt(UUID id, Order order) {
        return new PaymentAttempt(id, order, PaymentProvider.NONE, DEFAULT_IDEMPOTENCY_KEY);
    }

    private static PaymentAttempt createPaymentAttemptWithStatus(UUID id, Order order, PaymentStatus status) {
        return new PaymentAttempt(id, order, PaymentProvider.NONE, DEFAULT_IDEMPOTENCY_KEY, status);
    }

    private static Customer createCustomer(UUID customerId) {
        return new Customer(customerId, "user_email", "user_name", CustomerStatus.ACTIVE);
    }

    private static Order createPendingOrder(UUID customerId) {
        return Order.builder()
                .customer(createCustomer(customerId))
                .currency(Currency.MXN)
                .build();
    }

    private static Order createOrderWithStatus(UUID customerId, OrderStatus status) {
        return Order.builder()
                .customer(createCustomer(customerId))
                .currency(Currency.MXN).status(status)
                .build();
    }

    private static OrderItem createOrderItem() {
        return OrderItem.builder()
                .sku(DEFAULT_SKU)
                .description(DEFAULT_DESCRIPTION)
                .unitPrice(DEFAULT_UNIT_PRICE)
                .quantity(DEFAULT_QUANTITY)
                .build();

    }

    private static CreateOrderItemRequest createOrderItemRequest() {
        return new CreateOrderItemRequest(
                DEFAULT_SKU,
                DEFAULT_QUANTITY);
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
