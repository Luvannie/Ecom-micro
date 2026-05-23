package com.ecom.cart.domain;

import com.ecom.cart.client.ProductSnapshot;

import java.math.BigDecimal;
import java.util.UUID;

public class CartItem {
    private UUID productId;
    private String productName;
    private BigDecimal unitPrice;
    private int quantity;
    private String imageUrl;

    public CartItem() {
    }

    public CartItem(ProductSnapshot product, int quantity) {
        this.productId = product.id();
        this.productName = product.name();
        this.unitPrice = product.price();
        this.quantity = quantity;
        this.imageUrl = product.imageUrl();
    }

    public void refresh(ProductSnapshot product, int quantity) {
        this.productName = product.name();
        this.unitPrice = product.price();
        this.imageUrl = product.imageUrl();
        this.quantity = quantity;
    }

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
