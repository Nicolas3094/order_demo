package com.orders.messages.orders_demo.app.payment.internal;

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

import com.orders.messages.orders_demo.app.payment.api.CreatePaymentAttemptRequest;
import com.orders.messages.orders_demo.app.payment.api.PaymentAttemptResponse;
import com.orders.messages.orders_demo.app.payment.api.PaymentFailedRequest;
import com.orders.messages.orders_demo.app.payment.api.PaymentSucceededRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("api/v1/orders")
public class PaymentAttemptController {

    private final PaymentAttemptService paymentAttemptService;

    public PaymentAttemptController(PaymentAttemptService paymentAttemptService) {
        this.paymentAttemptService = paymentAttemptService;
    }

    /*
     * Payment Attempt endpoints.
     *
     * TODO: These state-transition endpoints are temporary and exist only for
     * development/testing. In production they will be triggered exclusively by
     * payment gateway webhooks (e.g. Stripe, Mercado Pago).
     */

    @GetMapping("/{orderId}/payments")
    public ResponseEntity<List<PaymentAttemptResponse>> getAllPayments(@PathVariable UUID orderId) {
        return ResponseEntity.ok(
                paymentAttemptService.getAllPayments(orderId)
                        .stream()
                        .map(PaymentAttemptMapper::toResponse)
                        .toList());
    }

    @GetMapping("/{orderId}/payments/{paymentId}")
    public ResponseEntity<PaymentAttemptResponse> getPayment(@PathVariable UUID orderId,
            @PathVariable UUID paymentId) {
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
                                orderId, paymentId,
                                paymentSucceededRequest.providerRef())));
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
                                orderId, paymentId, paymentFailedRequest.code(),
                                paymentFailedRequest.errorMessage())));
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
