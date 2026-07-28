package com.orders.messages.orders_demo.dtos.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload used to change the description of a product.
 *
 * @param description the new description to set for the product.
 */
public record ChangeDescriptionRequest(
        @NotBlank(message = "Description cannot be blank") String description) {

}
