package com.ecom.user.domain;

import com.ecom.user.web.dto.CreateAddressRequest;
import com.ecom.user.web.dto.UpdateAddressRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_addresses")
public class UserAddress {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String line1;

    private String line2;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String district;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAddress() {
    }

    public UserAddress(UUID userId, CreateAddressRequest request, boolean defaultAddress) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        apply(request.recipientName(), request.phone(), request.line1(), request.line2(), request.city(), request.district(), request.postalCode(), defaultAddress);
        this.createdAt = this.updatedAt;
    }

    public void update(UpdateAddressRequest request) {
        apply(request.recipientName(), request.phone(), request.line1(), request.line2(), request.city(), request.district(), request.postalCode(), request.defaultAddress());
    }

    public void setDefaultAddress(boolean defaultAddress) {
        this.defaultAddress = defaultAddress;
        this.updatedAt = Instant.now();
    }

    private void apply(String recipientName, String phone, String line1, String line2, String city, String district, String postalCode, boolean defaultAddress) {
        this.recipientName = recipientName;
        this.phone = phone;
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.district = district;
        this.postalCode = postalCode;
        this.defaultAddress = defaultAddress;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getPhone() {
        return phone;
    }

    public String getLine1() {
        return line1;
    }

    public String getLine2() {
        return line2;
    }

    public String getCity() {
        return city;
    }

    public String getDistrict() {
        return district;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public boolean isDefaultAddress() {
        return defaultAddress;
    }
}
