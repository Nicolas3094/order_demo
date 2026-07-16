package com.orders.messages.orders_demo.dtos.response;

import java.time.Instant;

/**
 * Standard error response returned by the REST API.
 *
 * @param timestamp time when the error occurred.
 * @param status    HTTP status code.
 * @param error     HTTP reason phrase.
 * @param message   detailed error message.
 * @param path      requested URI.
 */
public record ErrorResponse(
                Instant timestamp,
                int status,
                String error,
                String message,
                String path) {
}