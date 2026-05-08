package com.example.user.service;

import com.example.auth.entity.Role;
import com.example.auth.entity.User;
import com.example.auth.repository.RoleRepository;
import com.example.auth.repository.UserRepository;
import com.example.notification.service.SyncNotificationSubscriptionsService;
import com.example.user.dto.CreateUserRequest;
import com.example.user.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

@Service
public class CreateUserService {

    private static final Pattern PW_PATTERN =
            Pattern.compile("^(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9]).{8,}$");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BCryptPasswordEncoder encoder;
    private final SyncNotificationSubscriptionsService syncNotificationSubscriptionsService;

    public CreateUserService(UserRepository userRepository,
                             RoleRepository roleRepository,
                             BCryptPasswordEncoder encoder,
                             SyncNotificationSubscriptionsService syncNotificationSubscriptionsService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.encoder = encoder;
        this.syncNotificationSubscriptionsService = syncNotificationSubscriptionsService;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (!PW_PATTERN.matcher(request.getPassword()).matches()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Password must be at least 8 characters with uppercase, lowercase and digit");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Email already exists");
        }
        if (userRepository.existsByEmployeeId(request.getEmployeeId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Employee ID already exists");
        }

        Role role = roleRepository.findByCode(request.getRole())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid role"));

        User user = new User();
        user.setEmployeeId(request.getEmployeeId());
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        user.setPasswordHash(encoder.encode(request.getPassword()));
        user.setRole(role);
        user.setActiveFlag(true);

        User saved = userRepository.save(user);
        syncNotificationSubscriptionsService.applyForUserRole(saved.getId(), role.getCode());
        return UserResponse.from(saved);
    }
}
