package com.example.user.dto;

import com.example.auth.entity.User;

import java.time.LocalDateTime;

public class UserResponse {

    private Long id;
    private String employeeId;
    private String email;
    private String name;
    private String role;
    private boolean activeFlag;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UserResponse from(User user) {
        UserResponse r = new UserResponse();
        r.id         = user.getId();
        r.employeeId = user.getEmployeeId();
        r.email      = user.getEmail();
        r.name       = user.getName();
        r.role       = user.getRole().getCode();
        r.activeFlag = user.isActiveFlag();
        r.createdAt  = user.getCreatedAt();
        r.updatedAt  = user.getUpdatedAt();
        return r;
    }

    public Long getId() { return id; }
    public String getEmployeeId() { return employeeId; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public boolean isActiveFlag() { return activeFlag; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
