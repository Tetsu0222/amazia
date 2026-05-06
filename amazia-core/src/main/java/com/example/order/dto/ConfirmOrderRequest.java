package com.example.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 注文確定 API リクエスト（設計書 r4「注文確定 API リクエスト形式」）。
 *
 * - 配送先住所は market_customers の現住所を Service 側で自動スナップショット作成。
 *   会員と異なる住所への配送は本フェーズではスコープ外。
 * - user_id は MarketSessionAuthFilter 経由で取得するため本 DTO には含まない。
 */
public class ConfirmOrderRequest {

    @NotNull
    @Positive
    private Long skuId;

    @NotNull
    @Min(1)
    private Integer quantity;

    @NotNull
    @Positive
    private Long paymentMethodId;

    @NotNull
    @Positive
    private Long shippingMethodId;

    /** 予約購入か否か。注文時はクライアントが明示的に指定する（在庫切れでの予約受付など） */
    private boolean preorder;

    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Long getPaymentMethodId() { return paymentMethodId; }
    public void setPaymentMethodId(Long paymentMethodId) { this.paymentMethodId = paymentMethodId; }
    public Long getShippingMethodId() { return shippingMethodId; }
    public void setShippingMethodId(Long shippingMethodId) { this.shippingMethodId = shippingMethodId; }
    public boolean isPreorder() { return preorder; }
    public void setPreorder(boolean preorder) { this.preorder = preorder; }
}
