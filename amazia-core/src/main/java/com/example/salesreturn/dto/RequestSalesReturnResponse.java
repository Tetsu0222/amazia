package com.example.salesreturn.dto;

/**
 * 返品申請レスポンス。
 *
 * 設計書 r4 / phase14 §返品申請。
 * 申請成功時に sales_return.id と現在ステータスを返す。
 */
public class RequestSalesReturnResponse {
    private final Long id;
    private final String status;

    public RequestSalesReturnResponse(Long id, String status) {
        this.id = id;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getStatus() { return status; }
}
