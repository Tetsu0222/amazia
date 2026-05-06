package com.example.salesreturn.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Console 返品管理画面向け sales_return 1 件分。
 *
 * 設計書 r4 / Amazia Console §返品管理 表示項目：
 *   - 顧客名 / 商品名+色+サイズ / 申請数量 / 状態 / 理由
 *   - 申請日 / 承認日 / 売上ID / 売上日
 *
 * 集計はせず、画面表示のための整形のみを行う（フィルタ・並び替えは Console 側で実装）。
 */
public class AdminSalesReturnItem {

    private final Long id;
    private final String status;
    private final Integer quantity;
    private final String reason;
    private final LocalDateTime createdAt;
    private final LocalDateTime approvedAt;
    private final Long approverId;

    private final Long salesId;
    private final LocalDate salesDate;
    private final Long customerId;
    private final String customerName;

    private final Long skuId;
    private final String productName;
    private final String color;
    private final String size;

    public AdminSalesReturnItem(Long id, String status, Integer quantity, String reason,
                                LocalDateTime createdAt, LocalDateTime approvedAt, Long approverId,
                                Long salesId, LocalDate salesDate,
                                Long customerId, String customerName,
                                Long skuId, String productName, String color, String size) {
        this.id = id;
        this.status = status;
        this.quantity = quantity;
        this.reason = reason;
        this.createdAt = createdAt;
        this.approvedAt = approvedAt;
        this.approverId = approverId;
        this.salesId = salesId;
        this.salesDate = salesDate;
        this.customerId = customerId;
        this.customerName = customerName;
        this.skuId = skuId;
        this.productName = productName;
        this.color = color;
        this.size = size;
    }

    public Long getId() { return id; }
    public String getStatus() { return status; }
    public Integer getQuantity() { return quantity; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public Long getApproverId() { return approverId; }
    public Long getSalesId() { return salesId; }
    public LocalDate getSalesDate() { return salesDate; }
    public Long getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public Long getSkuId() { return skuId; }
    public String getProductName() { return productName; }
    public String getColor() { return color; }
    public String getSize() { return size; }
}
