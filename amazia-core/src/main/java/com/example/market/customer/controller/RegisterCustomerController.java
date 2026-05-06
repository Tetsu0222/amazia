package com.example.market.customer.controller;

import com.example.market.customer.dto.CustomerResponse;
import com.example.market.customer.dto.RegisterCustomerRequest;
import com.example.market.customer.service.RegisterCustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer")
public class RegisterCustomerController {

    private final RegisterCustomerService service;

    public RegisterCustomerController(RegisterCustomerService service) {
        this.service = service;
    }

    @PostMapping("/register")
    public ResponseEntity<CustomerResponse> register(@Valid @RequestBody RegisterCustomerRequest request) {
        CustomerResponse body = new CustomerResponse(service.register(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
