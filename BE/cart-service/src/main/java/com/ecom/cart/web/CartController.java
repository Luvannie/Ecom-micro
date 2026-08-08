package com.ecom.cart.web;

import com.ecom.common.security.GatewayUserContext;
import com.ecom.cart.service.CartService;
import com.ecom.cart.web.dto.AddCartItemRequest;
import com.ecom.cart.web.dto.CartResponse;
import com.ecom.cart.web.dto.UpdateCartItemRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/api/cart")
    public CartResponse getCart(HttpServletRequest request) {
        return CartResponse.from(cartService.getCart(context(request).userId()));
    }

    @PostMapping("/api/cart/items")
    public CartResponse addItem(HttpServletRequest request, @Valid @RequestBody AddCartItemRequest body) {
        return CartResponse.from(cartService.addItem(context(request).userId(), body.productId(), body.quantity()));
    }

    @PutMapping("/api/cart/items/{productId}")
    public CartResponse updateItem(HttpServletRequest request, @PathVariable("productId") UUID productId,
                                   @Valid @RequestBody UpdateCartItemRequest body) {
        return CartResponse.from(cartService.updateQuantity(context(request).userId(), productId, body.quantity()));
    }

    @DeleteMapping("/api/cart/items/{productId}")
    public CartResponse removeItem(HttpServletRequest request, @PathVariable("productId") UUID productId) {
        return CartResponse.from(cartService.removeItem(context(request).userId(), productId));
    }

    @DeleteMapping("/api/cart")
    public ResponseEntity<Void> clear(HttpServletRequest request) {
        cartService.clear(context(request).userId());
        return ResponseEntity.noContent().build();
    }

    private GatewayUserContext context(HttpServletRequest request) {
        return GatewayUserContext.from(request);
    }
}
