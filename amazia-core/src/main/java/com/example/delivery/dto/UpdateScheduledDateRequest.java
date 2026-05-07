package com.example.delivery.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 配送予定日変更リクエスト（PATCH /api/deliveries/{id}/scheduled-date）。
 *
 * <p>手動更新時は Service 層が {@code [manual]} プレフィックスを comment 先頭に固定付与する（RRR-5）。
 */
public class UpdateScheduledDateRequest {

    @NotNull
    private LocalDate scheduledDate;

    /** 変更理由（任意フリーテキスト。Service 層で [manual] プレフィックス自動付与） */
    private String reason;

    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
