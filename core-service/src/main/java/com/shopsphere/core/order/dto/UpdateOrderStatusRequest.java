package com.shopsphere.core.order.dto;

import com.shopsphere.core.order.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(@NotNull OrderStatus status) {}
