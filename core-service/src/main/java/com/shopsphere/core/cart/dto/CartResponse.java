package com.shopsphere.core.cart.dto;

import com.shopsphere.core.cart.entity.Cart;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(Long id, Long userId, List<CartItemResponse> items, BigDecimal total) {
    public static CartResponse from(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream().map(CartItemResponse::from).toList();
        BigDecimal total = items.stream()
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(cart.getId(), cart.getUserId(), items, total);
    }
}
