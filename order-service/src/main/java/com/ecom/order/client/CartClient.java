package com.ecom.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(name = "cart-service")
public interface CartClient {
    @GetMapping("/api/cart")
    CartResponse getCart(@RequestHeader("X-User-Id") UUID userId, @RequestHeader("X-User-Email") String email);

    @DeleteMapping("/api/cart")
    void clearCart(@RequestHeader("X-User-Id") UUID userId, @RequestHeader("X-User-Email") String email);
}
