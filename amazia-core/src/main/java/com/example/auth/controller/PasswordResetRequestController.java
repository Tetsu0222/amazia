package com.example.auth.controller;

import com.example.auth.dto.PasswordResetRequestDto;
import com.example.auth.service.PasswordResetRequestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/password/reset")
public class PasswordResetRequestController {

    private final PasswordResetRequestService service;

    public PasswordResetRequestController(PasswordResetRequestService service) {
        this.service = service;
    }

    @PostMapping("/request")
    public ResponseEntity<Void> request(@Valid @RequestBody PasswordResetRequestDto dto) {
        service.request(dto.getEmail());
        return ResponseEntity.ok().build();
    }
}
