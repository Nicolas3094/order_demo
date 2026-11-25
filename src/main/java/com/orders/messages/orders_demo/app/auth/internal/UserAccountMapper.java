package com.orders.messages.orders_demo.app.auth.internal;

import java.util.UUID;

import com.orders.messages.orders_demo.app.auth.api.UserAccountResponse;

public final class UserAccountMapper {

    public static UserAccountResponse toResponse(UserAccountEntity entity) {
        return new UserAccountResponse(entity.getId(), entity.getEmail(), entity.getRole());
    }

    public static UserAccountEntity toEntity(String email, String passwordHash, UUID customerId) {
        return UserAccountEntity.builder()
                .customerId(customerId)
                .passwordHash(passwordHash)
                .email(email)
                .build();
    }

}
