package com.orders.messages.orders_demo.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orders.messages.orders_demo.dtos.request.CreateOrderRequest;
import com.orders.messages.orders_demo.dtos.response.OrderResponse;
import com.orders.messages.orders_demo.mappers.OrderMapper;
import com.orders.messages.orders_demo.services.OrderService;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@RequestParam UUID id) {
        return ResponseEntity.ok(
                OrderMapper.toResponse(
                        orderService.getOrder(id)));

    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody @Validated CreateOrderRequest createOrderRequest) {
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

    @PatchMapping("/{id}/pay")
    public ResponseEntity<OrderResponse> payOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(
                OrderMapper.toResponse(
                        orderService.payOrder(id)));
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
