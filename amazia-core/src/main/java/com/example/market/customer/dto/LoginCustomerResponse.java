package com.example.market.customer.dto;

public class LoginCustomerResponse {

    private final Long customerId;
    private final String email;
    private final String csrfToken;

    public LoginCustomerResponse(Long customerId, String email, String csrfToken) {
        this.customerId = customerId;
        this.email = email;
        this.csrfToken = csrfToken;
    }

    public Long getCustomerId() { return customerId; }
    public String getEmail() { return email; }
    public String getCsrfToken() { return csrfToken; }
}
