package com.shopsphere.core.cart.dto;

import com.shopsphere.core.cart.entity.CartItem;

import java.math.BigDecimal;

public record CartItemResponse(
        Long id, Long productId, String productName,
        BigDecimal unitPrice, Integer quantity, BigDecimal lineTotal
) {
    public static CartItemResponse from(CartItem item) {
        return new CartItemResponse(item.getId(), item.getProductId(), item.getProductName(),
                item.getUnitPrice(), item.getQuantity(), item.lineTotal());
    }
}
