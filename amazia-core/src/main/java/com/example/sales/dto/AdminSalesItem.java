package com.example.sales.dto;

import java.time.LocalDate;

/**
 * Console 売上管理画面向け売上 1 件分。
 *
 * 設計書 r4 / Amazia Console §売上管理 表示項目：
 *   - ユーザ名 / 購入商品（名 + 色 + サイズ）/ 数量 / 金額 / 配送日 / 売上日
 *   - 配送ステータス / 決済方法 / 配送方法 / 予約 or 通常購入区分
 *
 * 集計はせず、画面表示のための整形のみを行う（フィルタ・並び替え・集計は Console 側で実装）。
 */
public class AdminSalesItem {

    private final Long salesId;
    private final LocalDate salesDate;
    private final LocalDate shippingDate;
    private final Long customerId;
    private final String customerName;
    private final Long skuId;
    private final String productName;
    private final String color;
    private final String size;
    private final Integer quantity;
    private final Integer amount;
    private final String shippingStatusCode;
    private final Long shippingMethodId;
    private final Long paymentMethodId;
    private final String paymentMethodName;
    private final boolean preorder;

    public AdminSalesItem(Long salesId, LocalDate salesDate, LocalDate shippingDate,
                          Long customerId, String customerName,
                          Long skuId, String productName, String color, String size,
                          Integer quantity, Integer amount,
                          String shippingStatusCode, Long shippingMethodId,
                          Long paymentMethodId, String paymentMethodName,
                          boolean preorder) {
        this.salesId = salesId;
        this.salesDate = salesDate;
        this.shippingDate = shippingDate;
        this.customerId = customerId;
        this.customerName = customerName;
        this.skuId = skuId;
        this.productName = productName;
        this.color = color;
        this.size = size;
        this.quantity = quantity;
        this.amount = amount;
        this.shippingStatusCode = shippingStatusCode;
        this.shippingMethodId = shippingMethodId;
        this.paymentMethodId = paymentMethodId;
        this.paymentMethodName = paymentMethodName;
        this.preorder = preorder;
    }

    public Long getSalesId() { return salesId; }
    public LocalDate getSalesDate() { return salesDate; }
    public LocalDate getShippingDate() { return shippingDate; }
    public Long getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public Long getSkuId() { return skuId; }
    public String getProductName() { return productName; }
    public String getColor() { return color; }
    public String getSize() { return size; }
    public Integer getQuantity() { return quantity; }
    public Integer getAmount() { return amount; }
    public String getShippingStatusCode() { return shippingStatusCode; }
    public Long getShippingMethodId() { return shippingMethodId; }
    public Long getPaymentMethodId() { return paymentMethodId; }
    public String getPaymentMethodName() { return paymentMethodName; }
    public boolean isPreorder() { return preorder; }
}
