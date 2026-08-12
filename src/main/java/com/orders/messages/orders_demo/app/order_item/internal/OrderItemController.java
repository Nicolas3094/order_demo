package com.orders.messages.orders_demo.app.order_item.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orders.messages.orders_demo.app.order_item.api.CreateOrderItemRequest;
import com.orders.messages.orders_demo.app.order_item.api.OrderItemChangeQuantityRequest;
import com.orders.messages.orders_demo.app.order_item.api.OrderItemChangeUnitPriceRequest;
import com.orders.messages.orders_demo.app.order_item.api.OrderItemResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("api/v1/orders")
public class OrderItemController {

    private final OrderItemService orderItemService;

    public OrderItemController(OrderItemService orderItemService) {
        this.orderItemService = orderItemService;
    }

    @GetMapping("/{orderId}/items")
    public ResponseEntity<List<OrderItemResponse>> getAllOrderItems(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderItemService.getAllOrderItems(orderId));
    }

    @GetMapping("/{orderId}/items/{orderItemId}")
    public ResponseEntity<OrderItemResponse> getOrderItem(
            @PathVariable UUID orderId,
            @PathVariable UUID orderItemId) {
        return ResponseEntity.ok(orderItemService.getOrderItem(orderId, orderItemId));
    }

    @PostMapping("/{orderId}/items")
    public ResponseEntity<OrderItemResponse> createOrderItem(
            @PathVariable UUID orderId,
            @Valid @RequestBody CreateOrderItemRequest itemRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderItemService.createOrderItem(orderId, itemRequest));
    }

    @DeleteMapping("/{orderId}/items/{orderItemId}")
    public ResponseEntity<Void> deleteOrderItem(
            @PathVariable UUID orderId,
            @PathVariable UUID orderItemId) {

        orderItemService.deleteOrderItem(orderId, orderItemId);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{orderId}/items/{orderItemId}/price")
    public ResponseEntity<OrderItemResponse> changeUnitPrice(
            @PathVariable UUID orderId,
            @PathVariable UUID orderItemId,
            @Valid @RequestBody OrderItemChangeUnitPriceRequest itemChangeUnitPriceRequest) {
        return ResponseEntity.ok(orderItemService.changeUnitPrice(
                orderId,
                orderItemId,
                itemChangeUnitPriceRequest.unitPrice()));
    }

    @PatchMapping("/{orderId}/items/{orderItemId}/quantity")
    public ResponseEntity<OrderItemResponse> changeQuantity(
            @PathVariable UUID orderId,
            @PathVariable UUID orderItemId,
            @Valid @RequestBody OrderItemChangeQuantityRequest orderItemChangeQuantityRequest) {
        return ResponseEntity.ok(orderItemService.changeQuantity(
                orderId,
                orderItemId,
                orderItemChangeQuantityRequest.quanity()));
    }

}
