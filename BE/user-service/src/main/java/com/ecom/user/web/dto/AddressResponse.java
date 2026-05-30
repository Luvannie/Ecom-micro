package com.ecom.user.web.dto;

import com.ecom.user.domain.UserAddress;

import java.util.UUID;

public record AddressResponse(
        UUID id,
        String recipientName,
        String phone,
        String line1,
        String line2,
        String city,
        String district,
        String postalCode,
        boolean defaultAddress
) {
    public static AddressResponse from(UserAddress address) {
        return new AddressResponse(address.getId(), address.getRecipientName(), address.getPhone(), address.getLine1(),
                address.getLine2(), address.getCity(), address.getDistrict(), address.getPostalCode(), address.isDefaultAddress());
    }
}
