package com.example.order.dto;

import com.example.sales.entity.Sales;

import java.time.LocalDate;

/**
 * 注文確定 API レスポンス。
 */
public class ConfirmOrderResponse {

    private final Long salesId;
    private final String paymentId;
    private final Long skuId;
    private final Integer quantity;
    private final Integer amount;
    private final LocalDate salesDate;
    private final boolean preorder;

    public ConfirmOrderResponse(Sales sales) {
        this.salesId = sales.getId();
        this.paymentId = sales.getPaymentId();
        this.skuId = sales.getSkuId();
        this.quantity = sales.getQuantity();
        this.amount = sales.getAmount();
        this.salesDate = sales.getSalesDate();
        this.preorder = sales.isPreorder();
    }

    public Long getSalesId() { return salesId; }
    public String getPaymentId() { return paymentId; }
    public Long getSkuId() { return skuId; }
    public Integer getQuantity() { return quantity; }
    public Integer getAmount() { return amount; }
    public LocalDate getSalesDate() { return salesDate; }
    public boolean isPreorder() { return preorder; }
}
