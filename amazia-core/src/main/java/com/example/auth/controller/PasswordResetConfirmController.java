package com.example.auth.controller;

import com.example.auth.dto.PasswordResetConfirmDto;
import com.example.auth.service.PasswordResetConfirmService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/password/reset")
public class PasswordResetConfirmController {

    private final PasswordResetConfirmService service;

    public PasswordResetConfirmController(PasswordResetConfirmService service) {
        this.service = service;
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody PasswordResetConfirmDto dto) {
        service.confirm(dto.getToken(), dto.getNewPassword());
        return ResponseEntity.ok().build();
    }
}
