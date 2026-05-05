package com.example.auth.dto;

public class LoginResponse {

    private String accessToken;
    private String role;

    public LoginResponse(String accessToken, String role) {
        this.accessToken = accessToken;
        this.role = role;
    }

    public String getAccessToken() { return accessToken; }
    public String getRole() { return role; }
}
