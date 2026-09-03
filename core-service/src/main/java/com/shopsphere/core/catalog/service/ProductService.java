package com.shopsphere.core.catalog.service;

import com.shopsphere.core.catalog.dto.ProductRequest;
import com.shopsphere.core.catalog.dto.ProductResponse;
import com.shopsphere.core.catalog.entity.Category;
import com.shopsphere.core.catalog.entity.Product;
import com.shopsphere.core.catalog.repository.ProductRepository;
import com.shopsphere.core.exception.InsufficientStockException;
import com.shopsphere.core.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;

    public ProductService(ProductRepository productRepository, CategoryService categoryService) {
        this.productRepository = productRepository;
        this.categoryService = categoryService;
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> findAll(Pageable pageable) {
        return productRepository.findByActiveTrue(pageable).map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> findByCategory(Long categoryId, Pageable pageable) {
        categoryService.getOrThrow(categoryId); // 404s if category doesn't exist
        return productRepository.findByCategoryIdAndActiveTrue(categoryId, pageable).map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> search(String name, Pageable pageable) {
        return productRepository.findByNameContainingIgnoreCaseAndActiveTrue(name, pageable).map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return ProductResponse.from(getOrThrow(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Category category = categoryService.getOrThrow(request.categoryId());
        Product product = Product.builder()
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .stockQuantity(request.stockQuantity())
                .imageUrl(request.imageUrl())
                .category(category)
                .active(true)
                .build();
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = getOrThrow(id);
        Category category = categoryService.getOrThrow(request.categoryId());

        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        product.setImageUrl(request.imageUrl());
        product.setCategory(category);

        return ProductResponse.from(product);
    }

    @Transactional
    public void delete(Long id) {
        Product product = getOrThrow(id);
        product.setActive(false); // soft delete: preserves history for past orders/reviews
    }

    /** Used by the order flow to atomically check-and-decrement stock inside the order transaction. */
    @Transactional
    public void reserveStock(Product product, int quantity) {
        if (!product.hasStock(quantity)) {
            throw new InsufficientStockException(product.getName(), product.getStockQuantity(), quantity);
        }
        product.setStockQuantity(product.getStockQuantity() - quantity);
    }

    public Product getOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", id));
    }
}
