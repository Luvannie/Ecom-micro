package com.ecom.cart.service;

import com.ecom.cart.client.ProductClient;
import com.ecom.cart.client.ProductSnapshot;
import com.ecom.cart.domain.Cart;
import com.ecom.cart.repository.CartRepository;
import com.ecom.common.web.ServiceUnavailableException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CartService {
    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    private final CartRepository cartRepository;
    private final ProductClient productClient;

    public CartService(CartRepository cartRepository, ProductClient productClient) {
        this.cartRepository = cartRepository;
        this.productClient = productClient;
    }

    public Cart getCart(UUID userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> new Cart(userId));
    }

    @CircuitBreaker(name = "productService", fallbackMethod = "addItemFallback")
    @Retry(name = "productService")
    @Bulkhead(name = "productService")
    public Cart addItem(UUID userId, UUID productId, int quantity) {
        validateQuantity(quantity);
        Cart cart = getCart(userId);
        cart.addOrUpdate(snapshot(productId), quantity);
        return cartRepository.save(cart);
    }

    @CircuitBreaker(name = "productService", fallbackMethod = "updateQuantityFallback")
    @Retry(name = "productService")
    @Bulkhead(name = "productService")
    public Cart updateQuantity(UUID userId, UUID productId, int quantity) {
        if (quantity < 0 || quantity > 99) {
            throw new InvalidCartQuantityException();
        }
        Cart cart = getCart(userId);
        if (quantity == 0) {
            cart.remove(productId);
        } else {
            cart.updateQuantity(snapshot(productId), quantity);
        }
        return cartRepository.save(cart);
    }

    public Cart removeItem(UUID userId, UUID productId) {
        Cart cart = getCart(userId);
        cart.remove(productId);
        return cartRepository.save(cart);
    }

    public void clear(UUID userId) {
        cartRepository.deleteByUserId(userId);
    }

    private ProductSnapshot snapshot(UUID productId) {
        ProductSnapshot product = productClient.getProduct(productId);
        if (product == null || !product.active()) {
            throw new ProductUnavailableException(productId);
        }
        return product;
    }

    private void validateQuantity(int quantity) {
        if (quantity < 1 || quantity > 99) {
            throw new InvalidCartQuantityException();
        }
    }

    private Cart addItemFallback(UUID userId, UUID productId, int quantity, Throwable t) {
        if (t instanceof InvalidCartQuantityException || t instanceof ProductUnavailableException) {
            // Preserve domain exceptions so business logic is not masked
            throw rethrowUnchecked(t);
        }
        log.warn("productService unavailable for addItem(userId={}, productId={}): {}",
                 userId, productId, t.getMessage());
        throw new ServiceUnavailableException("product-service", t);
    }

    private Cart updateQuantityFallback(UUID userId, UUID productId, int quantity, Throwable t) {
        if (t instanceof InvalidCartQuantityException || t instanceof ProductUnavailableException) {
            throw rethrowUnchecked(t);
        }
        log.warn("productService unavailable for updateQuantity(userId={}, productId={}): {}",
                 userId, productId, t.getMessage());
        throw new ServiceUnavailableException("product-service", t);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> E rethrowUnchecked(Throwable t) throws E {
        throw (E) t;
    }
}
