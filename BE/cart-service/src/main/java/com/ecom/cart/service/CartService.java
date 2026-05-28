package com.ecom.cart.service;

import com.ecom.cart.client.ProductClient;
import com.ecom.cart.client.ProductSnapshot;
import com.ecom.cart.domain.Cart;
import com.ecom.cart.repository.CartRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CartService {
    private final CartRepository cartRepository;
    private final ProductClient productClient;

    public CartService(CartRepository cartRepository, ProductClient productClient) {
        this.cartRepository = cartRepository;
        this.productClient = productClient;
    }

    public Cart getCart(UUID userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> new Cart(userId));
    }

    public Cart addItem(UUID userId, UUID productId, int quantity) {
        validateQuantity(quantity);
        Cart cart = getCart(userId);
        cart.addOrUpdate(snapshot(productId), quantity);
        return cartRepository.save(cart);
    }

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
}
