package com.ecom.product.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ecom.product.web.dto.ProductResponse;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Configure Spring Cache (Redis-backed) to use JSON serialization instead of
 * the default JDK binary serializer. The JDK serializer requires entities to
 * implement {@code Serializable}, and even then Hibernate's
 * {@code @ManyToOne(LAZY)} proxies cannot survive a session-less round trip.
 *
 * <p>Each cache is configured with a typed {@link Jackson2JsonRedisSerializer}
 * so deserialization produces the correct concrete class (no LinkedHashMap
 * fallback). Hibernate proxy scaffolding is stripped via
 * {@link JacksonHibernateMixIn}.
 */
@Configuration
public class CacheConfig {

    private final CacheProperties cacheProperties;

    public CacheConfig(CacheProperties cacheProperties) {
        this.cacheProperties = cacheProperties;
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerCustomizer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.addMixIn(Object.class, JacksonHibernateMixIn.class);

        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(resolveTtl())
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new Jackson2JsonRedisSerializer<>(mapper, ProductResponse.class)));

        return builder -> builder
                .cacheDefaults(defaults)
                .withCacheConfiguration("product-detail",
                        defaults.entryTtl(Duration.ofMinutes(10)))
                .withCacheConfiguration("product-search",
                        defaults.entryTtl(Duration.ofMinutes(2)));
    }

    private Duration resolveTtl() {
        Duration ttl = cacheProperties.getRedis().getTimeToLive();
        return ttl != null ? ttl : Duration.ofMinutes(5);
    }

    /**
     * Strip Hibernate's lazy-loading scaffolding from JSON output. Without this,
     * serializing an entity with an unresolved {@code @ManyToOne(LAZY)} proxy
     * throws "No serializer found for ByteBuddyInterceptor".
     */
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "$_hibernate_interceptor"})
    private static final class JacksonHibernateMixIn {
    }
}
