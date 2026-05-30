package com.ecom.cart.web;

import com.ecom.cart.client.ProductClient;
import com.ecom.cart.client.ProductSnapshot;
import com.ecom.cart.domain.Cart;
import com.ecom.cart.repository.CartRepository;
import com.ecom.cart.web.dto.AddCartItemRequest;
import com.ecom.cart.web.dto.UpdateCartItemRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CartControllerIT {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductClient productClient;

    @MockBean
    private CartRepository cartRepository;

    private final Map<UUID, Cart> carts = new HashMap<>();

    @BeforeEach
    void setUp() {
        carts.clear();
        Mockito.reset(productClient, cartRepository);
        when(cartRepository.findByUserId(any())).thenAnswer(invocation -> Optional.ofNullable(carts.get(invocation.getArgument(0))));
        when(cartRepository.save(any())).thenAnswer(invocation -> {
            Cart cart = invocation.getArgument(0);
            carts.put(cart.getUserId(), cart);
            return cart;
        });
        Mockito.doAnswer(invocation -> {
            carts.remove(invocation.getArgument(0));
            return null;
        }).when(cartRepository).deleteByUserId(any());
    }

    @Test
    void unauthenticatedRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/cart/items"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addItemReturnsCartTotal() throws Exception {
        UserContext user = user();
        UUID productId = UUID.randomUUID();
        when(productClient.getProduct(productId)).thenReturn(snapshot(productId, "Pizza", "10.00"));

        mockMvc.perform(post("/api/cart/items")
                        .headers(user.headers())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(20.00))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void updateItemChangesQuantity() throws Exception {
        UserContext user = user();
        UUID productId = UUID.randomUUID();
        when(productClient.getProduct(productId)).thenReturn(snapshot(productId, "Pizza", "10.00"));
        seedCart(user.userId(), productId);

        mockMvc.perform(put("/api/cart/items/{productId}", productId)
                        .headers(user.headers())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCartItemRequest(3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.total").value(30.00));
    }

    @Test
    void removeItemDeletesItem() throws Exception {
        UserContext user = user();
        UUID productId = UUID.randomUUID();
        seedCart(user.userId(), productId);

        mockMvc.perform(delete("/api/cart/items/{productId}", productId).headers(user.headers()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void clearReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/cart").headers(user().headers()))
                .andExpect(status().isNoContent());
    }

    private void seedCart(UUID userId, UUID productId) {
        Cart cart = new Cart(userId);
        cart.addOrUpdate(snapshot(productId, "Pizza", "10.00"), 1);
        carts.put(userId, cart);
    }

    private ProductSnapshot snapshot(UUID productId, String name, String price) {
        return new ProductSnapshot(productId, name, new BigDecimal(price), null, true);
    }

    private UserContext user() {
        UUID id = UUID.randomUUID();
        return new UserContext(id, "user-" + id + "@example.com");
    }

    private record UserContext(UUID userId, String email) {
        HttpHeaders headers() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-User-Id", userId.toString());
            headers.add("X-User-Email", email);
            return headers;
        }
    }
}
