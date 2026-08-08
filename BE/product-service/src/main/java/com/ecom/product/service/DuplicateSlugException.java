package com.ecom.product.service;

import com.ecom.common.web.ConflictException;

public class DuplicateSlugException extends ConflictException {
    public DuplicateSlugException(String slug) {
        super("Slug already exists: " + slug);
    }
}
