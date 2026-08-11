package com.orders.messages.orders_demo.app.order_item.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import com.orders.messages.orders_demo.app.common.enums.Currency;
import com.orders.messages.orders_demo.app.customer.internal.CustomerEntity;
import com.orders.messages.orders_demo.app.customer.internal.CustomerStatus;
import com.orders.messages.orders_demo.app.order.internal.OrderEntity;
import com.orders.messages.orders_demo.app.order.internal.exceptions.OrderNotFoundException;
import com.orders.messages.orders_demo.app.order_item.api.CreateOrderItemRequest;
import com.orders.messages.orders_demo.app.order_item.api.OrderItemChangeQuantityRequest;
import com.orders.messages.orders_demo.app.order_item.api.OrderItemChangeUnitPriceRequest;
import com.orders.messages.orders_demo.app.order_item.internal.exceptions.InvalidOrderItemStateException;
import com.orders.messages.orders_demo.app.order_item.internal.exceptions.OrderItemNotFoundException;

@WebMvcTest(OrderItemController.class)
public class OrderItemControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderItemService orderItemService;

    private UUID customerId;
    private UUID orderId;
    private UUID orderItemId;

    private static final String DEFAULT_SKU = "sku";
    private static final String DEFAULT_DESCRIPTION = "description";
    private static final BigDecimal DEFAULT_UNIT_PRICE = new BigDecimal("123.00");
    private static final Long DEFAULT_QUANTITY = 1L;

    @BeforeEach
    public void setup() {
        customerId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        orderItemId = UUID.randomUUID();
    }

    @Test
    public void getAllOrderItems_ShouldReturn200() throws Exception {
        OrderItemEntity orderItem1 = createOrderItem();
        OrderItemEntity orderItem2 = createOrderItem();
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
        OrderEntity order = createPendingOrder(customerId);
        OrderItemEntity orderItem = createOrderItem();
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
        OrderEntity order = createPendingOrder(customerId);
        OrderItemEntity orderItem = createOrderItem();
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
        OrderEntity order = createPendingOrder(customerId);
        OrderItemEntity orderItem = createOrderItem();
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
        OrderEntity order = createPendingOrder(customerId);
        OrderItemEntity orderItem = createOrderItem();
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

    private static CustomerEntity createCustomer(UUID customerId) {
        return new CustomerEntity(customerId, "user_email", "user_name", CustomerStatus.ACTIVE);
    }

    private static OrderEntity createPendingOrder(UUID customerId) {
        return OrderEntity.builder()
                .customer(createCustomer(customerId))
                .currency(Currency.MXN)
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

    private static CreateOrderItemRequest createOrderItemRequest() {
        return new CreateOrderItemRequest(
                DEFAULT_SKU,
                DEFAULT_QUANTITY);
    }

}
