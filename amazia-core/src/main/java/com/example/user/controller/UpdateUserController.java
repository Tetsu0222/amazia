package com.example.user.controller;

import com.example.user.dto.UpdateUserRequest;
import com.example.user.dto.UserResponse;
import com.example.user.service.UpdateUserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UpdateUserController {

    private final UpdateUserService updateUserService;

    public UpdateUserController(UpdateUserService updateUserService) {
        this.updateUserService = updateUserService;
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(updateUserService.update(id, request));
    }
}
