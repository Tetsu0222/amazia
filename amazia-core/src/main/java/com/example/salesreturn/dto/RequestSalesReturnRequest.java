package com.example.salesreturn.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 会員からの返品申請リクエスト。
 *
 * 設計書 r4 / phase14 §返品申請。
 * Market 側からの POST /api/customer/sales-returns で受け取る。
 */
public class RequestSalesReturnRequest {

    @NotNull(message = "salesId is required")
    private Long salesId;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity;

    @Size(max = 1000, message = "reason must be at most 1000 chars")
    private String reason;

    public Long getSalesId() { return salesId; }
    public void setSalesId(Long salesId) { this.salesId = salesId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
