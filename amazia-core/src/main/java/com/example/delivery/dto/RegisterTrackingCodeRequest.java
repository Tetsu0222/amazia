package com.example.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 追跡番号登録リクエスト（PATCH /api/deliveries/{id}/tracking-code）。
 */
public class RegisterTrackingCodeRequest {

    @NotBlank
    @Size(max = 100)
    private String trackingCode;

    public String getTrackingCode() { return trackingCode; }
    public void setTrackingCode(String trackingCode) { this.trackingCode = trackingCode; }
}
