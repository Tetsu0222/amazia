package com.example.market.customer.controller;

import com.example.market.customer.dto.LoginCustomerRequest;
import com.example.market.customer.dto.LoginCustomerResponse;
import com.example.market.customer.service.LoginCustomerService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer")
public class LoginCustomerController {

    private final LoginCustomerService service;

    public LoginCustomerController(LoginCustomerService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginCustomerResponse> login(@Valid @RequestBody LoginCustomerRequest request,
                                                       HttpServletResponse response) {
        return ResponseEntity.ok(service.login(request, response));
    }
}
