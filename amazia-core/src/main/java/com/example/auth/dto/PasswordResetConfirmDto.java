package com.example.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class PasswordResetConfirmDto {

    @NotBlank
    private String token;

    @NotBlank
    private String newPassword;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
