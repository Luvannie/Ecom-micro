package com.ecom.user.web;

import com.ecom.user.security.GatewayUserContext;
import com.ecom.user.service.UserProfileService;
import com.ecom.user.web.dto.AddressResponse;
import com.ecom.user.web.dto.CreateAddressRequest;
import com.ecom.user.web.dto.ProfileResponse;
import com.ecom.user.web.dto.UpdateAddressRequest;
import com.ecom.user.web.dto.UpdateProfileRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/me")
public class UserProfileController {
    private final UserProfileService service;

    public UserProfileController(UserProfileService service) {
        this.service = service;
    }

    @GetMapping
    ProfileResponse me(HttpServletRequest request) {
        GatewayUserContext context = GatewayUserContext.from(request);
        return ProfileResponse.from(service.getOrCreateProfile(context.userId(), context.email()));
    }

    @PutMapping
    ProfileResponse update(HttpServletRequest request, @Valid @RequestBody UpdateProfileRequest update) {
        GatewayUserContext context = GatewayUserContext.from(request);
        service.getOrCreateProfile(context.userId(), context.email());
        return ProfileResponse.from(service.updateProfile(context.userId(), update));
    }

    @GetMapping("/addresses")
    List<AddressResponse> addresses(HttpServletRequest request) {
        GatewayUserContext context = GatewayUserContext.from(request);
        return service.listAddresses(context.userId()).stream().map(AddressResponse::from).toList();
    }

    @PostMapping("/addresses")
    ResponseEntity<AddressResponse> addAddress(HttpServletRequest request, @Valid @RequestBody CreateAddressRequest create) {
        GatewayUserContext context = GatewayUserContext.from(request);
        AddressResponse response = AddressResponse.from(service.addAddress(context.userId(), create));
        return ResponseEntity.created(URI.create("/api/users/me/addresses/" + response.id())).body(response);
    }

    @PutMapping("/addresses/{addressId}")
    AddressResponse updateAddress(HttpServletRequest request, @PathVariable("addressId") UUID addressId,
                                  @Valid @RequestBody UpdateAddressRequest update) {
        GatewayUserContext context = GatewayUserContext.from(request);
        return AddressResponse.from(service.updateAddress(context.userId(), addressId, update));
    }

    @DeleteMapping("/addresses/{addressId}")
    ResponseEntity<Void> deleteAddress(HttpServletRequest request, @PathVariable("addressId") UUID addressId) {
        GatewayUserContext context = GatewayUserContext.from(request);
        service.deleteAddress(context.userId(), addressId);
        return ResponseEntity.noContent().build();
    }
}
