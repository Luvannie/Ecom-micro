package com.ecom.product.web;

import com.ecom.product.service.CategoryNotFoundException;
import com.ecom.product.service.DuplicateSlugException;
import com.ecom.product.service.ProductNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class CatalogExceptionHandler {
    @ExceptionHandler({CategoryNotFoundException.class, ProductNotFoundException.class})
    ResponseEntity<Map<String, String>> notFound(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(DuplicateSlugException.class)
    ResponseEntity<Map<String, String>> conflict(DuplicateSlugException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", "Validation failed"));
    }
}
