package com.shopsphere.core.review.dto;

import com.shopsphere.core.review.entity.Review;

import java.time.Instant;

public record ReviewResponse(
        Long id, Long productId, Long userId, String userName,
        Integer rating, String comment, Instant createdAt
) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(review.getId(), review.getProductId(), review.getUserId(),
                review.getUserName(), review.getRating(), review.getComment(), review.getCreatedAt());
    }
}
