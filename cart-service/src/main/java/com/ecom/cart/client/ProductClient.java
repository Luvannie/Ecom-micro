package com.ecom.cart.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "${clients.product-service.name:product-service}")
public interface ProductClient {
    @GetMapping("/api/products/{productId}")
    ProductSnapshot getProduct(@PathVariable("productId") UUID productId);
}
