package com.ecom.cart.domain;

import com.ecom.cart.client.ProductSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Cart {
    private UUID userId;
    private List<CartItem> items = new ArrayList<>();
    private Instant updatedAt;

    public Cart() {
    }

    public Cart(UUID userId) {
        this.userId = userId;
        this.updatedAt = Instant.now();
    }

    public void addOrUpdate(ProductSnapshot product, int quantity) {
        items.stream()
                .filter(item -> item.getProductId().equals(product.id()))
                .findFirst()
                .ifPresentOrElse(
                        item -> item.refresh(product, Math.min(99, item.getQuantity() + quantity)),
                        () -> items.add(new CartItem(product, quantity)));
        touch();
    }

    public void updateQuantity(ProductSnapshot product, int quantity) {
        remove(product.id());
        if (quantity > 0) {
            items.add(new CartItem(product, quantity));
        }
        touch();
    }

    public void remove(UUID productId) {
        items.removeIf(item -> item.getProductId().equals(productId));
        touch();
    }

    public BigDecimal total() {
        return items.stream()
                .map(CartItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public List<CartItem> getItems() {
        return items;
    }

    public void setItems(List<CartItem> items) {
        this.items = items;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
