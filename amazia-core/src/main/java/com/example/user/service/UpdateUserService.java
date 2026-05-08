package com.example.user.service;

import com.example.auth.entity.Role;
import com.example.auth.entity.User;
import com.example.auth.repository.RoleRepository;
import com.example.auth.repository.UserRepository;
import com.example.notification.service.SyncNotificationSubscriptionsService;
import com.example.user.dto.UpdateUserRequest;
import com.example.user.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UpdateUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SyncNotificationSubscriptionsService syncNotificationSubscriptionsService;

    public UpdateUserService(UserRepository userRepository,
                             RoleRepository roleRepository,
                             SyncNotificationSubscriptionsService syncNotificationSubscriptionsService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.syncNotificationSubscriptionsService = syncNotificationSubscriptionsService;
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Role role = roleRepository.findByCode(request.getRole())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid role"));

        user.setEmail(request.getEmail());
        user.setName(request.getName());
        user.setRole(role);
        if (request.getActiveFlag() != null) {
            user.setActiveFlag(request.getActiveFlag());
        }

        User saved = userRepository.save(user);
        syncNotificationSubscriptionsService.applyForUserRole(saved.getId(), role.getCode());
        return UserResponse.from(saved);
    }
}
