package com.shopsphere.core.catalog.dto;

import com.shopsphere.core.catalog.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id, String name, String description, BigDecimal price,
        Integer stockQuantity, String imageUrl, Long categoryId, String categoryName,
        boolean active, Instant createdAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(), product.getName(), product.getDescription(), product.getPrice(),
                product.getStockQuantity(), product.getImageUrl(),
                product.getCategory().getId(), product.getCategory().getName(),
                product.isActive(), product.getCreatedAt()
        );
    }
}
