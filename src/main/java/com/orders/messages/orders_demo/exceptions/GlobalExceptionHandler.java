package com.orders.messages.orders_demo.exceptions;

import java.time.Instant;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.orders.messages.orders_demo.dtos.response.ErrorResponse;
import com.orders.messages.orders_demo.exceptions.customer.CustomerNotFoundException;
import com.orders.messages.orders_demo.exceptions.customer.CustomerStateException;
import com.orders.messages.orders_demo.exceptions.orders.InvalidOrderStateException;
import com.orders.messages.orders_demo.exceptions.orders.OrderNotFoundException;
import com.orders.messages.orders_demo.exceptions.payment.InvalidPaymentStateException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Global exception handler for the REST API.
 * <p>
 * Centralizes the translation of application exceptions into standardized
 * HTTP responses, ensuring consistent error payloads across all endpoints.
 * </p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles requests for non-existing customers.
     *
     * @param e       the thrown {@link CustomerNotFoundException}.
     * @param request the current HTTP request.
     * @return a {@code 404 Not Found} response containing the standardized error
     *         payload.
     */
    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCustomerNotFound(
            CustomerNotFoundException e, HttpServletRequest request) {
        return handleNotFound(e, request);
    }

    /**
     * Handles requests for non-existing orders.
     *
     * @param e       the thrown {@link OrderNotFoundException}.
     * @param request the current HTTP request.
     * @return a {@code 404 Not Found} response containing the standardized error
     *         payload.
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(
            OrderNotFoundException e, HttpServletRequest request) {
        return handleNotFound(e, request);
    }

    /**
     * Handles generic requests for invalid order states.
     *
     * @param e       the thrown {@link InvalidOrderStateException}.
     * @param request the current HTTP request.
     * @return a {@code 409 Conflict} response containing the standardized error
     *         payload.
     */
    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOrderState(
            InvalidOrderStateException e, HttpServletRequest request) {
        return handleInvalid(e, request);
    }

    /**
     * Handles generic requests for invalid payment states.
     *
     * @param e       the thrown {@link InvalidPaymentStateException}.
     * @param request the current HTTP request.
     * @return a {@code 409 Conflict} response containing the standardized error
     *         payload.
     */
    @ExceptionHandler(InvalidPaymentStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInvalidPaymentState(
            InvalidPaymentStateException e, HttpServletRequest request) {
        return handleInvalid(e, request);
    }

    /**
     * Handles generic requests for invalid customer states.
     *
     * @param e       the thrown {@link CustomerStateException}.
     * @param request the current HTTP request.
     * @return a {@code 409 Conflict} response containing the standardized error
     *         payload.
     */
    @ExceptionHandler(CustomerStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCustomertate(
            CustomerStateException e, HttpServletRequest request) {
        return handleInvalid(e, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException e,
            HttpServletRequest request) {

        String message = Optional.ofNullable(e.getBindingResult())
                .map(BindingResult::getFieldError)
                .map(FieldError::getDefaultMessage)
                .orElse("Bad request.");

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message,
                request.getRequestURI());

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles unexpected exceptions not explicitly mapped by the application.
     *
     * @param e       the unexpected exception.
     * @param request the current HTTP request.
     * @return a {@code 500 Internal Server Error} response containing the
     *         standardized error payload.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception e, HttpServletRequest request) {

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Unexpected server error.",
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    /**
     * Handles generic requests for conflict errors.
     *
     * @param e       the thrown {@link OrderNotFoundException}.
     * @param request the current HTTP request.
     * @return a {@code 409 Conflict} response containing the standardized error
     *         payload.
     */
    public static ResponseEntity<ErrorResponse> handleInvalid(RuntimeException e, HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                e.getMessage(),
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(response);
    }

    /**
     * Handles generic requests for non-existing objects.
     * 
     * @param e       the runtime generic exception.
     * @param request the current HTTP request.
     * @return a {@code 404 Not Found} response containing the standardized error
     *         payload.
     */
    private static ResponseEntity<ErrorResponse> handleNotFound(RuntimeException e, HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                e.getMessage(),
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(response);
    }

}