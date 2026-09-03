package com.shopsphere.core.order.dto;

import com.shopsphere.core.order.entity.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(Long productId, String productName, BigDecimal unitPrice, Integer quantity, BigDecimal lineTotal) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getProductId(), item.getProductName(), item.getUnitPrice(),
                item.getQuantity(), item.lineTotal());
    }
}
