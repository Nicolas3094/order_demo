package com.orders.messages.orders_demo.app.auth.api;

import java.util.UUID;

import com.orders.messages.orders_demo.app.auth.internal.UserRole;

public record UserAccountResponse(
        UUID id,
        String email,
        UserRole role) {

}
