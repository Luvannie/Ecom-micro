package com.ecom.cart.repository;

import com.ecom.cart.domain.Cart;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CartRepository {
    private static final Duration TTL = Duration.ofDays(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public CartRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<Cart> findByUserId(UUID userId) {
        String value = redisTemplate.opsForValue().get(key(userId));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, Cart.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read cart " + userId, exception);
        }
    }

    public Cart save(Cart cart) {
        try {
            redisTemplate.opsForValue().set(key(cart.getUserId()), objectMapper.writeValueAsString(cart), TTL);
            return cart;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to write cart " + cart.getUserId(), exception);
        }
    }

    public void deleteByUserId(UUID userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(UUID userId) {
        return "cart:" + userId;
    }
}
