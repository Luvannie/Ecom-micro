package com.ecom.product.service;

import com.ecom.common.web.NotFoundException;

import java.util.UUID;

public class CategoryNotFoundException extends NotFoundException {
    public CategoryNotFoundException(UUID categoryId) {
        super("Category", categoryId);
    }
}
