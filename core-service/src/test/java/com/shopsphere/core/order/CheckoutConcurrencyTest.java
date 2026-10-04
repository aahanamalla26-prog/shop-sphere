package com.shopsphere.core.order;

import com.shopsphere.core.cart.dto.AddCartItemRequest;
import com.shopsphere.core.cart.repository.CartRepository;
import com.shopsphere.core.cart.service.CartService;
import com.shopsphere.core.catalog.entity.Category;
import com.shopsphere.core.catalog.entity.Product;
import com.shopsphere.core.catalog.repository.CategoryRepository;
import com.shopsphere.core.catalog.repository.ProductRepository;
import com.shopsphere.core.catalog.service.ProductService;
import com.shopsphere.core.exception.ForbiddenOperationException;
import com.shopsphere.core.exception.InsufficientStockException;
import com.shopsphere.core.order.dto.CreateOrderRequest;
import com.shopsphere.core.order.repository.OrderRepository;
import com.shopsphere.core.order.service.OrderService;
import com.shopsphere.core.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves checkout is safe under concurrency: stock never goes negative, no unit is sold twice,
 * and cancelling the same order twice restocks only once.
 * Runs on in-memory H2 so it needs no external database.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shopsphere_test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class CheckoutConcurrencyTest {

    private static final int THREADS = 20;

    @Autowired ProductService productService;
    @Autowired OrderService orderService;
    @Autowired CartService cartService;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired CartRepository cartRepository;
    @Autowired PlatformTransactionManager txManager;

    private Category category;

    @BeforeEach
    void setUp() {
        cleanUp();
        category = categoryRepository.save(Category.builder().name("Test category").build());
    }

    @AfterEach
    void cleanUp() {
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    private Product productWithStock(int stock) {
        return productRepository.save(Product.builder()
                .name("Limited item")
                .price(new BigDecimal("9.99"))
                .stockQuantity(stock)
                .category(category)
                .build());
    }

    private int stockOf(Long productId) {
        return productRepository.findStockQuantityById(productId).orElseThrow();
    }

    /** Runs all tasks at the same instant on separate threads and returns their results/errors. */
    private <T> List<Object> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    return task.call();
                } catch (Throwable t) {
                    return t;
                }
            }));
        }
        ready.await(10, TimeUnit.SECONDS);
        go.countDown();
        List<Object> results = new CopyOnWriteArrayList<>();
        for (Future<Object> f : futures) {
            results.add(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return results;
    }

    @Test
    void reserveStock_with20ThreadsAndStockOf5_succeedsExactly5Times() throws Exception {
        Product product = productWithStock(5);
        TransactionTemplate tx = new TransactionTemplate(txManager);

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> {
                tx.executeWithoutResult(status -> {
                    Product fresh = productRepository.findById(product.getId()).orElseThrow();
                    productService.reserveStock(fresh, 1);
                });
                return true;
            });
        }

        List<Object> results = runConcurrently(tasks);

        long ok = results.stream().filter(r -> Boolean.TRUE.equals(r)).count();
        long rejected = results.stream().filter(r -> r instanceof InsufficientStockException).count();
        assertThat(ok).isEqualTo(5);
        assertThat(rejected).isEqualTo(THREADS - 5);
        assertThat(stockOf(product.getId())).isZero();
    }

    @Test
    void concurrentCheckouts_createExactlyAsManyOrdersAsStock() throws Exception {
        int stock = 5;
        Product product = productWithStock(stock);

        for (long userId = 1; userId <= THREADS; userId++) {
            cartService.addItem(userId, new AddCartItemRequest(product.getId(), 1));
        }

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (long userId = 1; userId <= THREADS; userId++) {
            final long uid = userId;
            tasks.add(() -> {
                orderService.createFromCart(uid, new CreateOrderRequest("1 Test Street"));
                return true;
            });
        }

        List<Object> results = runConcurrently(tasks);

        long ok = results.stream().filter(r -> Boolean.TRUE.equals(r)).count();
        long rejected = results.stream().filter(r -> r instanceof InsufficientStockException).count();
        assertThat(ok).as("successful checkouts").isEqualTo(stock);
        assertThat(rejected).as("rejected for insufficient stock").isEqualTo(THREADS - stock);
        assertThat(orderRepository.count()).as("orders persisted (no partial/phantom orders)").isEqualTo(stock);
        assertThat(stockOf(product.getId())).as("remaining stock").isZero();
    }

    @Test
    void cancelOrder_twice_restocksOnlyOnce() {
        Product product = productWithStock(5);
        cartService.addItem(1L, new AddCartItemRequest(product.getId(), 2));
        orderService.createFromCart(1L, new CreateOrderRequest("1 Test Street"));
        assertThat(stockOf(product.getId())).isEqualTo(3);

        Long orderId = orderRepository.findAll().get(0).getId();
        UserPrincipal caller = new UserPrincipal(1L, "user@test.com", "ROLE_USER");

        orderService.cancel(caller, orderId);
        assertThat(stockOf(product.getId())).isEqualTo(5);

        assertThatThrownBy(() -> orderService.cancel(caller, orderId))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThat(stockOf(product.getId())).as("no double restock").isEqualTo(5);
    }

    @Test
    void cancelOrder_concurrently_restocksOnlyOnce() throws Exception {
        Product product = productWithStock(5);
        cartService.addItem(1L, new AddCartItemRequest(product.getId(), 2));
        orderService.createFromCart(1L, new CreateOrderRequest("1 Test Street"));
        Long orderId = orderRepository.findAll().get(0).getId();
        UserPrincipal caller = new UserPrincipal(1L, "user@test.com", "ROLE_USER");

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> {
                orderService.cancel(caller, orderId);
                return true;
            });
        }
        List<Object> results = runConcurrently(tasks);

        AtomicInteger succeeded = new AtomicInteger();
        results.forEach(r -> { if (Boolean.TRUE.equals(r)) succeeded.incrementAndGet(); });
        assertThat(succeeded.get()).as("only one cancel wins").isEqualTo(1);
        assertThat(stockOf(product.getId())).as("stock restored exactly once").isEqualTo(5);
    }
}
