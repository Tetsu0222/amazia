package com.example.salesreturn.dto;

import com.example.salesreturn.entity.SalesReturn;

import java.time.LocalDateTime;

/**
 * 管理者向け sales_return レスポンス（B-5-2 / B-5-3 で使用）。
 *
 * Entity をそのまま返すと無関係なフィールドや遅延フェッチが混入するため、
 * 必要項目のみに絞った DTO で返す。
 */
public class SalesReturnResponse {

    private final Long id;
    private final Long salesId;
    private final String status;
    private final Integer quantity;
    private final String reason;
    private final Long approverId;
    private final LocalDateTime approvedAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public SalesReturnResponse(SalesReturn ret) {
        this.id = ret.getId();
        this.salesId = ret.getSalesId();
        this.status = ret.getStatus();
        this.quantity = ret.getQuantity();
        this.reason = ret.getReason();
        this.approverId = ret.getApproverId();
        this.approvedAt = ret.getApprovedAt();
        this.createdAt = ret.getCreatedAt();
        this.updatedAt = ret.getUpdatedAt();
    }

    public Long getId() { return id; }
    public Long getSalesId() { return salesId; }
    public String getStatus() { return status; }
    public Integer getQuantity() { return quantity; }
    public String getReason() { return reason; }
    public Long getApproverId() { return approverId; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
