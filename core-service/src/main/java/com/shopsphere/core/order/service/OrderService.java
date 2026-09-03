package com.shopsphere.core.order.service;

import com.shopsphere.core.cart.entity.Cart;
import com.shopsphere.core.cart.entity.CartItem;
import com.shopsphere.core.cart.service.CartService;
import com.shopsphere.core.catalog.entity.Product;
import com.shopsphere.core.catalog.service.ProductService;
import com.shopsphere.core.exception.ForbiddenOperationException;
import com.shopsphere.core.exception.ResourceNotFoundException;
import com.shopsphere.core.order.dto.CreateOrderRequest;
import com.shopsphere.core.order.dto.OrderResponse;
import com.shopsphere.core.order.entity.Order;
import com.shopsphere.core.order.entity.OrderItem;
import com.shopsphere.core.order.entity.OrderStatus;
import com.shopsphere.core.order.repository.OrderRepository;
import com.shopsphere.core.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final ProductService productService;

    public OrderService(OrderRepository orderRepository, CartService cartService, ProductService productService) {
        this.orderRepository = orderRepository;
        this.cartService = cartService;
        this.productService = productService;
    }

    /**
     * Checkout: validates and atomically decrements stock for every cart line,
     * builds the order, and empties the cart -- all within a single transaction.
     * If stock is insufficient for ANY item, the whole operation rolls back so
     * we never end up with a partially-placed order or over-sold inventory.
     */
    @Transactional
    public OrderResponse createFromCart(Long userId, CreateOrderRequest request) {
        Cart cart = cartService.getNonEmptyCart(userId);

        Order order = Order.builder()
                .userId(userId)
                .shippingAddress(request.shippingAddress())
                .status(OrderStatus.PENDING)
                .build();

        BigDecimal total = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {
            Product product = productService.getOrThrow(cartItem.getProductId());
            productService.reserveStock(product, cartItem.getQuantity()); // throws InsufficientStockException -> rolls back txn

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .productId(product.getId())
                    .productName(product.getName())
                    .unitPrice(product.getPrice())
                    .quantity(cartItem.getQuantity())
                    .build();

            order.getItems().add(orderItem);
            total = total.add(orderItem.lineTotal());
        }

        order.setTotalAmount(total);
        Order saved = orderRepository.save(order);
        cartService.clearCart(userId);

        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> findForUser(Long userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable).map(OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> findAll(Pageable pageable) {
        return orderRepository.findAll(pageable).map(OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(UserPrincipal caller, Long orderId) {
        Order order = getOrThrow(orderId);
        assertOwnerOrAdmin(caller, order);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse updateStatus(Long orderId, OrderStatus newStatus) {
        Order order = getOrThrow(orderId);
        order.setStatus(newStatus);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancel(UserPrincipal caller, Long orderId) {
        Order order = getOrThrow(orderId);
        assertOwnerOrAdmin(caller, order);

        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new ForbiddenOperationException("Order has already shipped and can no longer be cancelled");
        }

        // Restock cancelled items.
        for (OrderItem item : order.getItems()) {
            Product product = productService.getOrThrow(item.getProductId());
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
        }

        order.setStatus(OrderStatus.CANCELLED);
        return OrderResponse.from(order);
    }

    private void assertOwnerOrAdmin(UserPrincipal caller, Order order) {
        if (!caller.isAdmin() && !order.getUserId().equals(caller.userId())) {
            throw new ForbiddenOperationException("You do not have access to this order");
        }
    }

    private Order getOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", id));
    }
}
