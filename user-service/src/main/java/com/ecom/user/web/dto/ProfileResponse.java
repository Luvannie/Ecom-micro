package com.ecom.user.web.dto;

import com.ecom.user.domain.UserProfile;

import java.util.UUID;

public record ProfileResponse(UUID id, String email, String displayName, String phone, String preferences) {
    public static ProfileResponse from(UserProfile profile) {
        return new ProfileResponse(profile.getId(), profile.getEmail(), profile.getDisplayName(), profile.getPhone(), profile.getPreferences());
    }
}
