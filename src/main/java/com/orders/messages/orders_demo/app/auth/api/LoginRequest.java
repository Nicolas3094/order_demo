package com.orders.messages.orders_demo.app.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "User email is required.") @Email(message = "Must be a well-formed email address.") @Size(max = 255, message = "Email size must be between 0 and 255.") String email,
        @NotBlank(message = "User password is required.") @Size(min = 12, max = 128, message = "Password must contain between 12 and 128 characters.") String password) {
}
