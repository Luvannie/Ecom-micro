package com.ecom.user.service;

import com.ecom.common.web.NotFoundException;

public class AddressNotFoundException extends NotFoundException {
    public AddressNotFoundException(String message) {
        super(message);
    }
}
