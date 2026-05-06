package com.example.market.customer.controller;

import com.example.market.customer.dto.PasswordResetConfirmCustomerRequest;
import com.example.market.customer.service.PasswordResetConfirmCustomerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer")
public class PasswordResetConfirmCustomerController {

    private final PasswordResetConfirmCustomerService service;

    public PasswordResetConfirmCustomerController(PasswordResetConfirmCustomerService service) {
        this.service = service;
    }

    @PostMapping("/password/reset/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody PasswordResetConfirmCustomerRequest request) {
        service.confirm(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }
}
