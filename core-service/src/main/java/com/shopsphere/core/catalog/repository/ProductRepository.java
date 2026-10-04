package com.shopsphere.core.catalog.repository;

import com.shopsphere.core.catalog.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Page<Product> findByActiveTrue(Pageable pageable);
    Page<Product> findByCategoryIdAndActiveTrue(Long categoryId, Pageable pageable);
    Page<Product> findByNameContainingIgnoreCaseAndActiveTrue(String name, Pageable pageable);

    /**
     * Atomic check-and-decrement done in ONE SQL statement. The WHERE clause makes the
     * stock check and the update indivisible, so concurrent checkouts can never push
     * stock below zero (no read-then-write race). Returns 1 if stock was reserved, 0 if not.
     */
    @Modifying
    @Query("update Product p set p.stockQuantity = p.stockQuantity - :qty "
         + "where p.id = :id and p.stockQuantity >= :qty")
    int decrementStockIfAvailable(@Param("id") Long id, @Param("qty") int qty);

    /** Atomic increment used when an order is cancelled (also avoids lost updates). */
    @Modifying
    @Query("update Product p set p.stockQuantity = p.stockQuantity + :qty where p.id = :id")
    int incrementStock(@Param("id") Long id, @Param("qty") int qty);

    /** Reads the current stock straight from the DB (bypasses the persistence-context cache). */
    @Query("select p.stockQuantity from Product p where p.id = :id")
    Optional<Integer> findStockQuantityById(@Param("id") Long id);
}
