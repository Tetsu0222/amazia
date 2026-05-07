package com.example.delivery.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 配送ステータス遷移リクエスト（PATCH /api/deliveries/{id}/status）。
 */
public class UpdateShippingStatusRequest {

    @NotNull
    @Positive
    private Long shippingStatusId;

    /** 遷移理由（任意フリーテキスト） */
    private String reason;

    public Long getShippingStatusId() { return shippingStatusId; }
    public void setShippingStatusId(Long shippingStatusId) { this.shippingStatusId = shippingStatusId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
