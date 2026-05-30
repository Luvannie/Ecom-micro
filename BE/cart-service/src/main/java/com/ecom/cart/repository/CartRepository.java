package com.ecom.cart.repository;

import com.ecom.cart.domain.Cart;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Repository
public class CartRepository {
    private static final Logger log = LoggerFactory.getLogger(CartRepository.class);
    private static final Duration TTL = Duration.ofDays(30);
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration OPERATION_TIMEOUT = Duration.ofMillis(100);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // Lua script for atomic get-or-create operation
    private static final String GET_OR_CREATE_SCRIPT = """
        local key = KEYS[1]
        local existing = redis.call('GET', key)
        if existing then
            return existing
        end
        return nil
        """;

    // Lua script for atomic save with optimistic locking
    private static final String SET_IF_SAME_SCRIPT = """
        local key = KEYS[1]
        local expectedVersion = ARGV[1]
        local newValue = ARGV[2]
        local ttl = ARGV[3]

        local current = redis.call('GET', key)
        if current then
            local currentObj = cjson.decode(current)
            local currentVersion = currentObj.version or 0
            if currentVersion ~= tonumber(expectedVersion) then
                return 'VERSION_MISMATCH'
            end
        end

        redis.call('SETEX', key, ttl, newValue)
        return 'OK'
        """;

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
            Cart cart = objectMapper.readValue(value, Cart.class);
            return Optional.of(cart);
        } catch (JsonProcessingException exception) {
            log.error("Unable to read cart {}: {}", userId, exception.getMessage());
            throw new IllegalStateException("Unable to read cart " + userId, exception);
        }
    }

    /**
     * Atomically get cart or create new one.
     * Uses distributed lock to prevent race conditions.
     */
    public Cart findByUserIdOrCreate(UUID userId) {
        String lockKey = "lock:" + key(userId);
        String cartKey = key(userId);

        // Try to acquire lock
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "locked", LOCK_TIMEOUT);

        if (Boolean.TRUE.equals(acquired)) {
            try {
                // Double-check inside lock
                String existing = redisTemplate.opsForValue().get(cartKey);
                if (existing != null) {
                    return objectMapper.readValue(existing, Cart.class);
                }
                Cart newCart = new Cart(userId);
                save(newCart);
                return newCart;
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Unable to read cart", e);
            } finally {
                // Release lock
                redisTemplate.delete(lockKey);
            }
        } else {
            // Wait for lock holder and retry
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return findByUserIdOrCreate(userId);
        }
    }

    public Cart save(Cart cart) {
        try {
            // Increment version for optimistic locking
            cart.setVersion(cart.getVersion() + 1);

            String jsonValue = objectMapper.writeValueAsString(cart);
            redisTemplate.opsForValue().set(key(cart.getUserId()), jsonValue, TTL);
            log.debug("Saved cart for userId={}, version={}", cart.getUserId(), cart.getVersion());
            return cart;
        } catch (JsonProcessingException exception) {
            log.error("Unable to write cart {}: {}", cart.getUserId(), exception.getMessage());
            throw new IllegalStateException("Unable to write cart " + cart.getUserId(), exception);
        }
    }

    /**
     * Atomic save with version check to prevent lost updates.
     * Returns true if save succeeded, false if version mismatch (concurrent modification).
     */
    public boolean saveWithVersionCheck(Cart cart, int expectedVersion) {
        try {
            cart.setVersion(cart.getVersion() + 1);
            String jsonValue = objectMapper.writeValueAsString(cart);

            DefaultRedisScript<String> script = new DefaultRedisScript<>(SET_IF_SAME_SCRIPT, String.class);
            String result = redisTemplate.execute(
                    script,
                    Collections.singletonList(key(cart.getUserId())),
                    String.valueOf(expectedVersion),
                    jsonValue,
                    String.valueOf(TTL.toSeconds())
            );

            if ("OK".equals(result)) {
                return true;
            } else if ("VERSION_MISMATCH".equals(result)) {
                log.warn("Version mismatch for cart userId={}, expected={}", cart.getUserId(), expectedVersion);
                return false;
            }
            return false;
        } catch (JsonProcessingException exception) {
            log.error("Unable to write cart {}: {}", cart.getUserId(), exception.getMessage());
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