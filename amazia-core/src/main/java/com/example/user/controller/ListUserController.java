package com.example.user.controller;

import com.example.user.dto.UserResponse;
import com.example.user.service.ListUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class ListUserController {

    private final ListUserService listUserService;

    public ListUserController(ListUserService listUserService) {
        this.listUserService = listUserService;
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> list() {
        return ResponseEntity.ok(listUserService.list());
    }
}
