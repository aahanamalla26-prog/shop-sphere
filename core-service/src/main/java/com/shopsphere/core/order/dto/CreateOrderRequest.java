package com.shopsphere.core.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Checkout takes place against the caller's current cart -- no line items here. */
public record CreateOrderRequest(
        @NotBlank @Size(max = 500) String shippingAddress
) {}
