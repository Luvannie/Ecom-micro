package com.ecom.cart.web;

import com.ecom.cart.service.InvalidCartQuantityException;
import com.ecom.cart.service.ProductUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class CartExceptionHandler {
    @ExceptionHandler({InvalidCartQuantityException.class, MethodArgumentNotValidException.class})
    ResponseEntity<Map<String, String>> badRequest(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid cart request"));
    }

    @ExceptionHandler(ProductUnavailableException.class)
    ResponseEntity<Map<String, String>> productUnavailable(ProductUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage()));
    }
}
