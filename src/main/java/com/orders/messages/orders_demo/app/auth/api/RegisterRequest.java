package com.orders.messages.orders_demo.app.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data required to register a customer account.
 *
 * @param email    account email address
 * @param password raw password to hash before storage
 * @param name     customer name
 */
public record RegisterRequest(
        @NotBlank(message = "User email is required.") @Email(message = "Must be a well-formed email address.") @Size(max = 255, message = "Email size must be between 0 and 255.") String email,
        @NotBlank(message = "User password is required.") @Size(min = 12, max = 128, message = "Password must contain between 12 and 128 characters.") String password,
        @NotBlank(message = "User name is required.") @Size(max = 100, message = "Name must not exceed 100 characters.") String name) {
}
