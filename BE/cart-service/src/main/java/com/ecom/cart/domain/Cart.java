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
    private int version = 0;  // For optimistic locking

    public Cart() {
    }

    public Cart(UUID userId) {
        this.userId = userId;
        this.updatedAt = Instant.now();
        this.version = 1;
    }

    public synchronized void addOrUpdate(ProductSnapshot product, int quantity) {
        boolean found = false;
        for (int i = 0; i < items.size(); i++) {
            CartItem item = items.get(i);
            if (item.getProductId().equals(product.id())) {
                item.refresh(product, Math.min(99, item.getQuantity() + quantity));
                found = true;
                break;
            }
        }
        if (!found) {
            items.add(new CartItem(product, quantity));
        }
        touch();
    }

    public synchronized void updateQuantity(ProductSnapshot product, int quantity) {
        remove(product.id());
        if (quantity > 0) {
            items.add(new CartItem(product, quantity));
        }
        touch();
    }

    public synchronized void remove(UUID productId) {
        items.removeIf(item -> item.getProductId().equals(productId));
        touch();
    }

    public synchronized BigDecimal total() {
        return items.stream()
                .map(CartItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void touch() {
        this.updatedAt = Instant.now();
        // Note: version is incremented by the repository on save
    }

    // Getters and setters
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

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}