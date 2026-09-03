package com.shopsphere.core.review.service;

import com.shopsphere.core.catalog.service.ProductService;
import com.shopsphere.core.exception.BadRequestException;
import com.shopsphere.core.exception.ForbiddenOperationException;
import com.shopsphere.core.exception.ResourceNotFoundException;
import com.shopsphere.core.review.dto.ReviewRequest;
import com.shopsphere.core.review.dto.ReviewResponse;
import com.shopsphere.core.review.entity.Review;
import com.shopsphere.core.review.repository.ReviewRepository;
import com.shopsphere.core.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductService productService;

    public ReviewService(ReviewRepository reviewRepository, ProductService productService) {
        this.reviewRepository = reviewRepository;
        this.productService = productService;
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> findByProduct(Long productId, Pageable pageable) {
        productService.getOrThrow(productId); // 404s if product doesn't exist
        return reviewRepository.findByProductId(productId, pageable).map(ReviewResponse::from);
    }

    @Transactional
    public ReviewResponse create(UserPrincipal caller, Long productId, ReviewRequest request) {
        productService.getOrThrow(productId);

        if (reviewRepository.findByProductIdAndUserId(productId, caller.userId()).isPresent()) {
            throw new BadRequestException("You have already reviewed this product");
        }

        Review review = Review.builder()
                .productId(productId)
                .userId(caller.userId())
                .userName(caller.email())
                .rating(request.rating())
                .comment(request.comment())
                .build();

        return ReviewResponse.from(reviewRepository.save(review));
    }

    @Transactional
    public ReviewResponse update(UserPrincipal caller, Long reviewId, ReviewRequest request) {
        Review review = getOrThrow(reviewId);
        assertOwner(caller, review);

        review.setRating(request.rating());
        review.setComment(request.comment());
        return ReviewResponse.from(review);
    }

    @Transactional
    public void delete(UserPrincipal caller, Long reviewId) {
        Review review = getOrThrow(reviewId);
        assertOwnerOrAdmin(caller, review);
        reviewRepository.delete(review);
    }

    private void assertOwner(UserPrincipal caller, Review review) {
        if (!review.getUserId().equals(caller.userId())) {
            throw new ForbiddenOperationException("You can only edit your own reviews");
        }
    }

    private void assertOwnerOrAdmin(UserPrincipal caller, Review review) {
        if (!caller.isAdmin() && !review.getUserId().equals(caller.userId())) {
            throw new ForbiddenOperationException("You can only delete your own reviews");
        }
    }

    private Review getOrThrow(Long id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Review", id));
    }
}
