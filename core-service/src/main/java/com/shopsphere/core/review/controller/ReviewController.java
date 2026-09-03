package com.shopsphere.core.review.controller;

import com.shopsphere.core.review.dto.ReviewRequest;
import com.shopsphere.core.review.dto.ReviewResponse;
import com.shopsphere.core.review.service.ReviewService;
import com.shopsphere.core.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReviewController {

    private final ReviewService reviewService;
    private final CurrentUser currentUser;

    public ReviewController(ReviewService reviewService, CurrentUser currentUser) {
        this.reviewService = reviewService;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/products/{productId}/reviews")
    public ResponseEntity<Page<ReviewResponse>> findByProduct(@PathVariable Long productId, Pageable pageable) {
        return ResponseEntity.ok(reviewService.findByProduct(productId, pageable));
    }

    @PostMapping("/api/products/{productId}/reviews")
    public ResponseEntity<ReviewResponse> create(@PathVariable Long productId,
                                                   @Valid @RequestBody ReviewRequest request) {
        ReviewResponse review = reviewService.create(currentUser.get(), productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(review);
    }

    @PutMapping("/api/reviews/{id}")
    public ResponseEntity<ReviewResponse> update(@PathVariable Long id, @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(reviewService.update(currentUser.get(), id, request));
    }

    @DeleteMapping("/api/reviews/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        reviewService.delete(currentUser.get(), id);
        return ResponseEntity.noContent().build();
    }
}
