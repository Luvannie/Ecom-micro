package com.ecom.cart.client;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSnapshot(UUID id, String name, BigDecimal price, String imageUrl, boolean active) {
}
