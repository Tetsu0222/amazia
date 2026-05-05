package com.example.user.dto;

import jakarta.validation.constraints.*;

public class UpdateUserRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(max = 50)
    private String name;

    @NotBlank
    private String role;

    private Boolean activeFlag;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Boolean getActiveFlag() { return activeFlag; }
    public void setActiveFlag(Boolean activeFlag) { this.activeFlag = activeFlag; }
}
