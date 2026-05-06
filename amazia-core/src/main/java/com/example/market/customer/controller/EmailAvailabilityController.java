package com.example.market.customer.controller;

import com.example.market.customer.dto.EmailAvailabilityResponse;
import com.example.market.customer.service.CheckEmailAvailabilityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer")
public class EmailAvailabilityController {

    private final CheckEmailAvailabilityService service;

    public EmailAvailabilityController(CheckEmailAvailabilityService service) {
        this.service = service;
    }

    @GetMapping("/email-availability")
    public ResponseEntity<EmailAvailabilityResponse> check(@RequestParam("email") String email) {
        return ResponseEntity.ok(new EmailAvailabilityResponse(service.isAvailable(email)));
    }
}
