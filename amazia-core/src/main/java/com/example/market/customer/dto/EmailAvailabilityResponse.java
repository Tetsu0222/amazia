package com.example.market.customer.dto;

public class EmailAvailabilityResponse {

    private final boolean available;

    public EmailAvailabilityResponse(boolean available) {
        this.available = available;
    }

    public boolean isAvailable() { return available; }
}
