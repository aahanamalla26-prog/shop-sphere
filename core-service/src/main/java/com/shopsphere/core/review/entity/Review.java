package com.shopsphere.core.review.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "reviews", uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "user_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String userName;

    @Column(nullable = false)
    private Integer rating; // 1-5

    @Column(length = 1000)
    private String comment;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
