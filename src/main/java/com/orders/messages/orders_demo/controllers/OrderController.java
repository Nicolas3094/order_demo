package com.orders.messages.orders_demo.controllers;

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

import com.orders.messages.orders_demo.dtos.request.CreateOrderRequest;
import com.orders.messages.orders_demo.dtos.request.CreatePaymentAttemptRequest;
import com.orders.messages.orders_demo.dtos.request.PaymentFailedRequest;
import com.orders.messages.orders_demo.dtos.request.PaymentSucceededRequest;
import com.orders.messages.orders_demo.dtos.response.OrderResponse;
import com.orders.messages.orders_demo.dtos.response.PaymentAttemptResponse;
import com.orders.messages.orders_demo.mappers.OrderMapper;
import com.orders.messages.orders_demo.mappers.PaymentAttemptMapper;
import com.orders.messages.orders_demo.services.OrderService;
import com.orders.messages.orders_demo.services.PaymentAttemptService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final PaymentAttemptService paymentAttemptService;

    public OrderController(OrderService orderService, PaymentAttemptService paymentAttemptService) {
        this.orderService = orderService;
        this.paymentAttemptService = paymentAttemptService;
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

    /*
     * Payment Attempt endpoints.
     *
     * TODO: These state-transition endpoints are temporary and exist only for
     * development/testing. In production they will be triggered exclusively by
     * payment gateway webhooks (e.g. Stripe, Mercado Pago).
     */

    @GetMapping("/{orderId}/payments/{paymentId}")
    public ResponseEntity<PaymentAttemptResponse> getPayment(@PathVariable UUID orderId, @PathVariable UUID paymentId) {
        return ResponseEntity.ok(
                PaymentAttemptMapper.toResponse(
                        paymentAttemptService.getPaymentAttempt(orderId, paymentId)));
    }

    @PostMapping("/{orderId}/payments")
    public ResponseEntity<PaymentAttemptResponse> createPayment(
            @PathVariable UUID orderId,
            @Valid @RequestBody CreatePaymentAttemptRequest paymentRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PaymentAttemptMapper.toResponse(
                        paymentAttemptService.createPaymentAttempt(orderId, paymentRequest)));
    }

    // TODO: Replace manual state transition with payment gateway webhook. This
    // endpoint is temporary for development and testing purposes.
    @PatchMapping("/{orderId}/payments/{paymentId}/processing")
    public ResponseEntity<PaymentAttemptResponse> startProcessing(
            @PathVariable UUID orderId,
            @PathVariable UUID paymentId) {
        return ResponseEntity.ok(
                PaymentAttemptMapper.toResponse(
                        paymentAttemptService.startProcessing(orderId, paymentId)));
    }

    // TODO: Replace manual state transition with payment gateway webhook. The
    // payment provider will notify successful payments asynchronously.
    @PatchMapping("/{orderId}/payments/{paymentId}/succeeded")
    public ResponseEntity<PaymentAttemptResponse> markPaymentAsSucceeded(
            @PathVariable UUID orderId,
            @PathVariable UUID paymentId,
            @RequestBody PaymentSucceededRequest paymentSucceededRequest) {
        return ResponseEntity.ok(
                PaymentAttemptMapper.toResponse(
                        paymentAttemptService.markAsSucceeded(
                                orderId, paymentId, paymentSucceededRequest.providerRef())));
    }

    // TODO: Replace manual state transition with payment gateway webhook. Failure
    // information will come directly from the payment provider.
    @PatchMapping("/{orderId}/payments/{paymentId}/failed")
    public ResponseEntity<PaymentAttemptResponse> markPaymentAsFailed(
            @PathVariable UUID orderId,
            @PathVariable UUID paymentId,
            @RequestBody PaymentFailedRequest paymentFailedRequest) {
        return ResponseEntity.ok(
                PaymentAttemptMapper.toResponse(
                        paymentAttemptService.markAsFailed(
                                orderId, paymentId, paymentFailedRequest.code(), paymentFailedRequest.errorMessage())));
    }

    // TODO: Replace manual state transition with payment gateway webhook or
    // internal payment orchestration. Clients should not invoke this endpoint.
    @PatchMapping("/{orderId}/payments/{paymentId}/cancel")
    public ResponseEntity<PaymentAttemptResponse> markPaymentAsCancelled(
            @PathVariable UUID orderId,
            @PathVariable UUID paymentId) {
        return ResponseEntity.ok(
                PaymentAttemptMapper.toResponse(
                        paymentAttemptService.markAsCancelled(orderId, paymentId)));
    }

}
