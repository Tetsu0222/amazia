package com.example.market.customer.controller;

import com.example.market.customer.dto.PasswordResetRequestCustomerRequest;
import com.example.market.customer.service.PasswordResetRequestCustomerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer")
public class PasswordResetRequestCustomerController {

    private final PasswordResetRequestCustomerService service;

    public PasswordResetRequestCustomerController(PasswordResetRequestCustomerService service) {
        this.service = service;
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> request(@Valid @RequestBody PasswordResetRequestCustomerRequest request) {
        service.request(request.getEmail());
        return ResponseEntity.ok().build();
    }
}
