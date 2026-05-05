package com.example.user.service;

import com.example.auth.repository.UserRepository;
import com.example.user.dto.UserResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListUserService {

    private final UserRepository userRepository;

    public ListUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserResponse> list() {
        return userRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }
}
