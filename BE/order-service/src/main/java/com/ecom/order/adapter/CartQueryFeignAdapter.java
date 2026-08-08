package com.ecom.order.adapter;

import com.ecom.order.client.CartClient;
import com.ecom.order.client.CartResponse;
import com.ecom.order.port.CartQueryPort;
import com.ecom.order.port.dto.CartItemView;
import com.ecom.order.port.dto.CartView;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class CartQueryFeignAdapter implements CartQueryPort {

    private final CartClient client;

    CartQueryFeignAdapter(CartClient client) {
        this.client = client;
    }

    @Override
    public CartView getCart(UUID userId, String email) {
        CartResponse r = client.getCart(userId, email);
        return new CartView(userId,
                r.items().stream()
                        .map(i -> new CartItemView(i.productId(), i.productName(), i.unitPrice(), i.quantity()))
                        .toList());
    }

    @Override
    public void clearCart(UUID userId, String email) {
        client.clearCart(userId, email);
    }
}
