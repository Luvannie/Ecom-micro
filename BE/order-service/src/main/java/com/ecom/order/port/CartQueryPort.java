package com.ecom.order.port;

import com.ecom.order.port.dto.CartView;

import java.util.UUID;

/** Port for reading the user's cart. Adapter implementations may use
 *  Feign (cart-service HTTP), an in-memory cache, or any other transport. */
public interface CartQueryPort {
    CartView getCart(UUID userId, String email);
    void clearCart(UUID userId, String email);
}
