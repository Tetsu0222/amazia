package com.example.delivery.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 配送先住所変更リクエスト（PATCH /api/deliveries/{id}/address）。
 *
 * <p>{@code shippingAddressId} は {@code sales.user_id} 所有の {@code address} のみ
 * 参照可能。Service 層のオーナー検証で強制する（RRR-7）。
 */
public class UpdateShippingAddressRequest {

    @NotNull
    @Positive
    private Long shippingAddressId;

    /** 変更理由（任意フリーテキスト） */
    private String reason;

    public Long getShippingAddressId() { return shippingAddressId; }
    public void setShippingAddressId(Long shippingAddressId) { this.shippingAddressId = shippingAddressId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
