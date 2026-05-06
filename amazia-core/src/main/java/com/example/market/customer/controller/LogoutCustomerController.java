package com.example.market.customer.controller;

import com.example.market.customer.service.LogoutCustomerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/customer")
public class LogoutCustomerController {

    private final LogoutCustomerService service;

    public LogoutCustomerController(LogoutCustomerService service) {
        this.service = service;
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Boolean>> logout(HttpServletRequest request,
                                                      HttpServletResponse response) {
        service.logout(request, response);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
