package com.orders.messages.orders_demo.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orders.messages.orders_demo.dtos.request.CreateOrderRequest;
import com.orders.messages.orders_demo.dtos.response.OrderResponse;
import com.orders.messages.orders_demo.mappers.OrderMapper;
import com.orders.messages.orders_demo.services.OrderService;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
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
    public OrderResponse getOrder(@RequestParam UUID id) {
        return OrderMapper.toResponse(orderService.getOrder(id));

    }

    @PostMapping
    public OrderResponse createOrder(@RequestBody CreateOrderRequest createOrderRequest) {
        return OrderMapper.toResponse(orderService.createOrder(createOrderRequest));
    }

}
