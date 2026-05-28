package com.ecom.user.service;

import com.ecom.user.domain.UserAddress;
import com.ecom.user.domain.UserProfile;
import com.ecom.user.repository.UserAddressRepository;
import com.ecom.user.repository.UserProfileRepository;
import com.ecom.user.web.dto.CreateAddressRequest;
import com.ecom.user.web.dto.UpdateAddressRequest;
import com.ecom.user.web.dto.UpdateProfileRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserProfileService {
    private final UserProfileRepository profiles;
    private final UserAddressRepository addresses;

    public UserProfileService(UserProfileRepository profiles, UserAddressRepository addresses) {
        this.profiles = profiles;
        this.addresses = addresses;
    }

    @Transactional
    public UserProfile getOrCreateProfile(UUID userId, String email) {
        return profiles.findById(userId).orElseGet(() -> profiles.save(new UserProfile(userId, email)));
    }

    @Transactional
    public UserProfile updateProfile(UUID userId, UpdateProfileRequest request) {
        UserProfile profile = profiles.findById(userId).orElseThrow(() -> new IllegalArgumentException("Profile not found"));
        profile.update(request.displayName(), request.phone(), request.preferences());
        return profile;
    }

    @Transactional(readOnly = true)
    public List<UserAddress> listAddresses(UUID userId) {
        return addresses.findByUserId(userId);
    }

    @Transactional
    public UserAddress addAddress(UUID userId, CreateAddressRequest request) {
        boolean makeDefault = request.defaultAddress() || addresses.findByUserId(userId).isEmpty();
        if (makeDefault) {
            clearDefault(userId);
        }
        return addresses.save(new UserAddress(userId, request, makeDefault));
    }

    @Transactional
    public UserAddress updateAddress(UUID userId, UUID addressId, UpdateAddressRequest request) {
        UserAddress address = addresses.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new AddressNotFoundException("Address not found"));
        if (request.defaultAddress()) {
            clearDefault(userId);
        }
        address.update(request);
        return address;
    }

    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        UserAddress address = addresses.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new AddressNotFoundException("Address not found"));
        addresses.delete(address);
    }

    private void clearDefault(UUID userId) {
        List<UserAddress> addressList = addresses.findByUserId(userId);
        addressList.forEach(address -> address.setDefaultAddress(false));
        addresses.saveAll(addressList);
    }
}
