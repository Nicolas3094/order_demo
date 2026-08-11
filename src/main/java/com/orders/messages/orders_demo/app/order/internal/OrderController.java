package com.orders.messages.orders_demo.app.order.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orders.messages.orders_demo.app.order.api.CreateOrderRequest;
import com.orders.messages.orders_demo.app.order.api.OrderResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        return ResponseEntity.ok(
                orderService.getAllOrders()
                        .stream()
                        .map(OrderMapper::toResponse)
                        .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(
                OrderMapper.toResponse(
                        orderService.getOrder(id)));
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest createOrderRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderMapper.toResponse(
                        orderService.createOrder(createOrderRequest)));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(
                OrderMapper.toResponse(
                        orderService.cancelOrder(id)));
    }

    @PatchMapping("/{id}/expire")
    public ResponseEntity<OrderResponse> expireOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(
                OrderMapper.toResponse(
                        orderService.expireOrder(id)));
    }

    @PatchMapping("/{id}/refund")
    public ResponseEntity<OrderResponse> refundOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(
                OrderMapper.toResponse(
                        orderService.refundOrder(id)));
    }

}
