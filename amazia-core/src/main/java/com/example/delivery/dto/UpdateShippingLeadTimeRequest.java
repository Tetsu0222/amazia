package com.example.delivery.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 都道府県別リードタイム更新リクエスト（PATCH /api/shipping-lead-times/{id}）。
 *
 * <p>{@code leadTimeDays} は 0 以上を許容（{@code lead_time_days = 0} で無効化運用 / 設計書 §設計上の注意）。
 */
public class UpdateShippingLeadTimeRequest {

    @NotNull
    @Min(0)
    private Integer leadTimeDays;

    public Integer getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(Integer leadTimeDays) { this.leadTimeDays = leadTimeDays; }
}
