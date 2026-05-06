package com.example.order.dto;

import java.time.LocalDate;

/**
 * 購入履歴 1 件分（Market 用）。
 *
 * 設計書 r4 / Amazia Market 購入履歴 表示項目：
 *   - 購入日時 / 商品名+色+サイズ / 数量 / 金額 / 配送予定日 / 配送ステータス / 配送方法 / 予約 or 通常購入区分
 */
public class PurchaseHistoryItem {

    private final Long salesId;
    private final LocalDate salesDate;
    private final LocalDate shippingDate;
    private final Long skuId;
    private final String productName;
    private final String color;
    private final String size;
    private final Integer quantity;
    private final Integer amount;
    private final String shippingStatusCode;
    private final Long shippingMethodId;
    private final Long paymentMethodId;
    private final boolean preorder;

    public PurchaseHistoryItem(Long salesId, LocalDate salesDate, LocalDate shippingDate,
                               Long skuId, String productName, String color, String size,
                               Integer quantity, Integer amount,
                               String shippingStatusCode, Long shippingMethodId,
                               Long paymentMethodId, boolean preorder) {
        this.salesId = salesId;
        this.salesDate = salesDate;
        this.shippingDate = shippingDate;
        this.skuId = skuId;
        this.productName = productName;
        this.color = color;
        this.size = size;
        this.quantity = quantity;
        this.amount = amount;
        this.shippingStatusCode = shippingStatusCode;
        this.shippingMethodId = shippingMethodId;
        this.paymentMethodId = paymentMethodId;
        this.preorder = preorder;
    }

    public Long getSalesId() { return salesId; }
    public LocalDate getSalesDate() { return salesDate; }
    public LocalDate getShippingDate() { return shippingDate; }
    public Long getSkuId() { return skuId; }
    public String getProductName() { return productName; }
    public String getColor() { return color; }
    public String getSize() { return size; }
    public Integer getQuantity() { return quantity; }
    public Integer getAmount() { return amount; }
    public String getShippingStatusCode() { return shippingStatusCode; }
    public Long getShippingMethodId() { return shippingMethodId; }
    public Long getPaymentMethodId() { return paymentMethodId; }
    public boolean isPreorder() { return preorder; }
}
