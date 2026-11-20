package com.orders.messages.orders_demo.app.order.internal;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.orders.messages.orders_demo.app.order.api.OrderResponse;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/orders")
public class CustomerOrderController {

    private final CustomerOrderService customerOrderService;

    public CustomerOrderController(CustomerOrderService customerOrderService) {
        this.customerOrderService = customerOrderService;
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrders(@PathVariable UUID orderId, @PathVariable UUID customerId) {
        return ResponseEntity.ok(customerOrderService.getOrder(orderId, customerId));
    }

    @GetMapping
    public ResponseEntity<Page<OrderResponse>> getCustomerOrders(
            @PathVariable UUID customerId) {
        return ResponseEntity
                .ok(customerOrderService.getCustomerOrders(
                        customerId,
                        OrderStatus.PENDING_PAYMENT,
                        PageRequest.of(0, 15, Sort.by("createdAt").descending())));
    }

    @GetMapping("?status={orderStatus}&page={page}&size={size}")
    public ResponseEntity<Page<OrderResponse>> getCustomerOrders(
            @PathVariable UUID customerId,
            @RequestParam(required = false) OrderStatus orderStatus,
            @PageableDefault(size = 15, sort = "createdAt", direction = Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(customerOrderService.getCustomerOrders(customerId, orderStatus, pageable));
    }
}
