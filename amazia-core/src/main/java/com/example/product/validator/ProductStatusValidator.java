package com.example.product.validator;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@Component
public class ProductStatusValidator {

    private static final Set<String> VALID_CODES = Set.of("WAITING", "RESERVATION", "ON_SALE");

    public void validate(String statusCode) {
        if (statusCode != null && !VALID_CODES.contains(statusCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status code: " + statusCode);
        }
    }
}
