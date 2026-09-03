package com.shopsphere.core.order.dto;

import com.shopsphere.core.order.entity.Order;
import com.shopsphere.core.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id, Long userId, List<OrderItemResponse> items, BigDecimal totalAmount,
        OrderStatus status, String shippingAddress, Instant createdAt, Instant updatedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(), order.getUserId(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getTotalAmount(), order.getStatus(), order.getShippingAddress(),
                order.getCreatedAt(), order.getUpdatedAt()
        );
    }
}
