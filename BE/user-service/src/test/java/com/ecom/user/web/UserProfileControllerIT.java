package com.ecom.user.web;

import com.ecom.user.web.dto.CreateAddressRequest;
import com.ecom.user.web.dto.UpdateAddressRequest;
import com.ecom.user.web.dto.UpdateProfileRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserProfileControllerIT {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void missingGatewayUserHeadersReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMeCreatesAndReturnsProfile() throws Exception {
        UserContext user = user();

        mockMvc.perform(get("/api/users/me").headers(user.headers()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.userId().toString()))
                .andExpect(jsonPath("$.email").value(user.email()));
    }

    @Test
    void putMeUpdatesProfile() throws Exception {
        UserContext user = user();
        mockMvc.perform(get("/api/users/me").headers(user.headers())).andExpect(status().isOk());

        mockMvc.perform(put("/api/users/me")
                        .headers(user.headers())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest("New Name", "0900000000", "{\"locale\":\"vi\"}"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("New Name"))
                .andExpect(jsonPath("$.phone").value("0900000000"));
    }

    @Test
    void addressCreateListUpdateDeleteWorksForCurrentUser() throws Exception {
        UserContext user = user();
        mockMvc.perform(get("/api/users/me").headers(user.headers())).andExpect(status().isOk());

        String createdBody = mockMvc.perform(post("/api/users/me/addresses")
                        .headers(user.headers())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(address("Demo Customer", true))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/users/me/addresses/")))
                .andExpect(jsonPath("$.defaultAddress").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String addressId = objectMapper.readTree(createdBody).get("id").asText();

        mockMvc.perform(get("/api/users/me/addresses").headers(user.headers()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(addressId));

        mockMvc.perform(put("/api/users/me/addresses/{addressId}", addressId)
                        .headers(user.headers())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAddressRequest(
                                "Updated Customer", "0911111111", "2 Main", null, "HCMC", "District 2", "700000", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientName").value("Updated Customer"));

        mockMvc.perform(delete("/api/users/me/addresses/{addressId}", addressId).headers(user.headers()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/me/addresses").headers(user.headers()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    private CreateAddressRequest address(String name, boolean defaultAddress) {
        return new CreateAddressRequest(name, "0900000000", "1 Main", null, "HCMC", "District 1", "700000", defaultAddress);
    }

    private UserContext user() {
        UUID id = UUID.randomUUID();
        return new UserContext(id, "user-" + id + "@example.com");
    }

    private record UserContext(UUID userId, String email) {
        org.springframework.http.HttpHeaders headers() {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.add("X-User-Id", userId.toString());
            headers.add("X-User-Email", email);
            headers.add("X-User-Roles", "CUSTOMER");
            return headers;
        }
    }
}
