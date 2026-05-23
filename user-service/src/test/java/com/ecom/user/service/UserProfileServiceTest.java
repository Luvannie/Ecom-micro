package com.ecom.user.service;

import com.ecom.user.web.dto.CreateAddressRequest;
import com.ecom.user.web.dto.UpdateAddressRequest;
import com.ecom.user.web.dto.UpdateProfileRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class UserProfileServiceTest {
    @Autowired
    private UserProfileService userProfileService;

    @Test
    void firstAccessCreatesProfileFromAuthenticatedContext() {
        UUID userId = UUID.randomUUID();

        var profile = userProfileService.getOrCreateProfile(userId, "customer@example.com");

        assertThat(profile.getId()).isEqualTo(userId);
        assertThat(profile.getEmail()).isEqualTo("customer@example.com");
        assertThat(profile.getDisplayName()).isEqualTo("customer");
    }

    @Test
    void updateProfileChangesDisplayNameAndPhone() {
        UUID userId = UUID.randomUUID();
        userProfileService.getOrCreateProfile(userId, "profile@example.com");

        var profile = userProfileService.updateProfile(userId, new UpdateProfileRequest("New Name", "0900000000", "{\"locale\":\"vi\"}"));

        assertThat(profile.getDisplayName()).isEqualTo("New Name");
        assertThat(profile.getPhone()).isEqualTo("0900000000");
    }

    @Test
    void settingSecondAddressDefaultClearsFirstDefault() {
        UUID userId = UUID.randomUUID();
        userProfileService.getOrCreateProfile(userId, "address@example.com");

        var first = userProfileService.addAddress(userId, address("First", true));
        var second = userProfileService.addAddress(userId, address("Second", true));

        assertThat(userProfileService.listAddresses(userId))
                .anySatisfy(address -> {
                    if (address.getId().equals(first.getId())) {
                        assertThat(address.isDefaultAddress()).isFalse();
                    }
                })
                .anySatisfy(address -> {
                    if (address.getId().equals(second.getId())) {
                        assertThat(address.isDefaultAddress()).isTrue();
                    }
                });
    }

    @Test
    void addingFirstAddressMarksItDefault() {
        UUID userId = UUID.randomUUID();
        userProfileService.getOrCreateProfile(userId, "first-address@example.com");

        var address = userProfileService.addAddress(userId, address("First", false));

        assertThat(address.isDefaultAddress()).isTrue();
    }

    @Test
    void userCannotUpdateAnotherUsersAddress() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        userProfileService.getOrCreateProfile(ownerId, "owner@example.com");
        userProfileService.getOrCreateProfile(otherId, "other@example.com");
        var address = userProfileService.addAddress(ownerId, address("Owner", true));

        assertThatThrownBy(() -> userProfileService.updateAddress(otherId, address.getId(),
                new UpdateAddressRequest("Other", "0900000000", "2 Main", null, "HCMC", "District 1", "700000", true)))
                .isInstanceOf(AddressNotFoundException.class);
    }

    private CreateAddressRequest address(String name, boolean defaultAddress) {
        return new CreateAddressRequest(name, "0900000000", "1 Main", null, "HCMC", "District 1", "700000", defaultAddress);
    }
}
