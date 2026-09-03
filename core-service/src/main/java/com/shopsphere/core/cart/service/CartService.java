package com.shopsphere.core.cart.service;

import com.shopsphere.core.cart.dto.AddCartItemRequest;
import com.shopsphere.core.cart.dto.CartResponse;
import com.shopsphere.core.cart.dto.UpdateCartItemRequest;
import com.shopsphere.core.cart.entity.Cart;
import com.shopsphere.core.cart.entity.CartItem;
import com.shopsphere.core.cart.repository.CartItemRepository;
import com.shopsphere.core.cart.repository.CartRepository;
import com.shopsphere.core.catalog.entity.Product;
import com.shopsphere.core.catalog.service.ProductService;
import com.shopsphere.core.exception.BadRequestException;
import com.shopsphere.core.exception.InsufficientStockException;
import com.shopsphere.core.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductService productService;

    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository, ProductService productService) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productService = productService;
    }

    @Transactional
    public CartResponse getOrCreateCart(Long userId) {
        return CartResponse.from(findOrCreateCart(userId));
    }

    @Transactional
    public CartResponse addItem(Long userId, AddCartItemRequest request) {
        Cart cart = findOrCreateCart(userId);
        Product product = productService.getOrThrow(request.productId());

        if (!product.hasStock(request.quantity())) {
            throw new InsufficientStockException(product.getName(), product.getStockQuantity(), request.quantity());
        }

        CartItem item = CartItem.builder()
                .productId(product.getId())
                .productName(product.getName())
                .unitPrice(product.getPrice())
                .quantity(request.quantity())
                .build();

        cart.addOrUpdateItem(item);
        return CartResponse.from(cart);
    }

    @Transactional
    public CartResponse updateItem(Long userId, Long itemId, UpdateCartItemRequest request) {
        Cart cart = findOrCreateCart(userId);
        CartItem item = cart.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("Cart item", itemId));

        Product product = productService.getOrThrow(item.getProductId());
        if (!product.hasStock(request.quantity())) {
            throw new InsufficientStockException(product.getName(), product.getStockQuantity(), request.quantity());
        }

        item.setQuantity(request.quantity());
        return CartResponse.from(cart);
    }

    @Transactional
    public CartResponse removeItem(Long userId, Long itemId) {
        Cart cart = findOrCreateCart(userId);
        boolean removed = cart.getItems().removeIf(i -> i.getId().equals(itemId));
        if (!removed) {
            throw new ResourceNotFoundException("Cart item with id " + itemId + " was not found in your cart");
        }
        return CartResponse.from(cart);
    }

    @Transactional
    public void clearCart(Long userId) {
        Cart cart = findOrCreateCart(userId);
        cart.getItems().clear();
    }

    /** Used by OrderService (checkout flow); throws if the cart is empty since an order needs items. */
    public Cart getNonEmptyCart(Long userId) {
        Cart cart = findOrCreateCart(userId);
        if (cart.getItems().isEmpty()) {
            throw new BadRequestException("Cannot proceed: your cart is empty");
        }
        return cart;
    }

    private Cart findOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> cartRepository.save(Cart.builder().userId(userId).build()));
    }
}
