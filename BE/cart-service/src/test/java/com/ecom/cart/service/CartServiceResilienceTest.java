package com.ecom.cart.service;

import com.ecom.cart.client.ProductClient;
import com.ecom.cart.client.ProductSnapshot;
import com.ecom.cart.repository.CartRepository;
import com.ecom.common.web.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Resilience4J integration test for CartService. Verifies that:
 * <ul>
 *   <li>When product-service Feign call fails with an IOException, the retry policy retries
 *       maxAttempts times before giving up.</li>
 *   <li>When the underlying call exhausts retries, the fallback method is invoked and
 *       throws ServiceUnavailableException with downstream = "product-service".</li>
 *   <li>When retry succeeds on a later attempt, the call returns normally.</li>
 * </ul>
 *
 * <p>The fallback only fires when Spring AOP is active (i.e., the bean is proxied by the
 * Resilience4J auto-configuration), so this test uses {@code @SpringBootTest} to load
 * the real application context.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class CartServiceResilienceTest {

    @Autowired
    private CartService cartService;

    @MockBean
    private ProductClient productClient;

    @MockBean
    private CartRepository cartRepository;

    @Test
    void productServiceDown_throwsServiceUnavailableException() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(new com.ecom.cart.domain.Cart(userId)));
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productClient.getProduct(productId))
            .thenAnswer(inv -> { throw new IOException("connection refused"); });

        // R4J retry: 3 attempts with 200ms exp backoff (~600ms total)
        assertThatThrownBy(() -> cartService.addItem(userId, productId, 1))
            .isInstanceOf(ServiceUnavailableException.class)
            .hasMessageContaining("product-service");

        verify(productClient, times(3)).getProduct(productId);
    }

    @Test
    void retry_succeedsOnSecondAttempt() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        ProductSnapshot snap = new ProductSnapshot(productId, "Pizza", new BigDecimal("10.00"), "url", true);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(new com.ecom.cart.domain.Cart(userId)));
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productClient.getProduct(productId))
            .thenAnswer(inv -> { throw new IOException("transient"); })
            .thenReturn(snap);

        var cart = cartService.addItem(userId, productId, 2);

        verify(productClient, times(2)).getProduct(productId);
        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getProductName()).isEqualTo("Pizza");
    }
}
