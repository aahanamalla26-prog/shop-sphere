package com.shopsphere.core.catalog.dto;

import com.shopsphere.core.catalog.entity.Category;

public record CategoryResponse(Long id, String name, String description, long productCount) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(),
                category.getDescription(), category.getProducts().size());
    }
}
