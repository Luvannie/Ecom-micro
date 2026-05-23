package com.ecom.cart.service;

import com.ecom.cart.client.ProductClient;
import com.ecom.cart.client.ProductSnapshot;
import com.ecom.cart.repository.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {
    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductClient productClient;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, productClient);
    }

    @Test
    void addItemStoresProductNameAndPriceFromProductService() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(productClient.getProduct(productId)).thenReturn(snapshot(productId, "Pizza", "12.50"));
        when(cartRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var cart = cartService.addItem(userId, productId, 2);

        assertThat(cart.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getProductName()).isEqualTo("Pizza");
            assertThat(item.getUnitPrice()).isEqualByComparingTo("12.50");
            assertThat(item.getQuantity()).isEqualTo(2);
            assertThat(item.subtotal()).isEqualByComparingTo("25.00");
        });
    }

    @Test
    void addSameProductIncrementsQuantity() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(new com.ecom.cart.domain.Cart(userId)));
        when(productClient.getProduct(productId)).thenReturn(snapshot(productId, "Pizza", "12.50"));
        when(cartRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var first = cartService.addItem(userId, productId, 2);
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(first));

        var second = cartService.addItem(userId, productId, 3);

        assertThat(second.getItems()).singleElement()
                .extracting("quantity")
                .isEqualTo(5);
    }

    @Test
    void updateQuantityToZeroRemovesItem() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        var cart = new com.ecom.cart.domain.Cart(userId);
        cart.addOrUpdate(snapshot(productId, "Pizza", "12.50"), 2);
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var updated = cartService.updateQuantity(userId, productId, 0);

        assertThat(updated.getItems()).isEmpty();
    }

    @Test
    void clearDeletesRedisCartKey() {
        UUID userId = UUID.randomUUID();

        cartService.clear(userId);

        verify(cartRepository).deleteByUserId(userId);
    }

    @Test
    void invalidQuantityReturnsValidationError() {
        assertThatThrownBy(() -> cartService.addItem(UUID.randomUUID(), UUID.randomUUID(), 100))
                .isInstanceOf(InvalidCartQuantityException.class);
    }

    private ProductSnapshot snapshot(UUID productId, String name, String price) {
        return new ProductSnapshot(productId, name, new BigDecimal(price), null, true);
    }
}
