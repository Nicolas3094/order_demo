package com.orders.messages.orders_demo.dtos.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload used to change the name of a product.
 *
 * @param name the new name to set for the product.
 */
public record ChangeNameRequest(
        @NotBlank(message = "Name cannot be blank") String name) {

}
