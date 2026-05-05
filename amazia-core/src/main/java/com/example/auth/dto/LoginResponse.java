package com.example.auth.dto;

public class LoginResponse {

    private String accessToken;
    private String role;
    private Long userId;

    public LoginResponse(String accessToken, String role) {
        this(accessToken, role, null);
    }

    public LoginResponse(String accessToken, String role, Long userId) {
        this.accessToken = accessToken;
        this.role = role;
        this.userId = userId;
    }

    public String getAccessToken() { return accessToken; }
    public String getRole()        { return role; }
    public Long   getUserId()      { return userId; }
}
