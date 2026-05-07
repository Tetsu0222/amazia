package com.example.order.dto;

import java.time.LocalDate;

/**
 * 購入履歴 1 件分（Market 用）。
 *
 * 設計書 r4 / Amazia Market 購入履歴 表示項目：
 *   - 購入日時 / 商品名+色+サイズ / 数量 / 金額 / 配送予定日 / 配送ステータス / 配送方法 / 予約 or 通常購入区分
 * フェーズ15 r5：deliveries テーブル由来の delivery ネストを追加（RR-4 / Step D）。
 *   - scheduled_date NULL のとき Market は「入荷待ち」と表示
 *   - shipped_date / delivered_date / tracking_code を表示拡張
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
    private final DeliveryInfo delivery;

    public PurchaseHistoryItem(Long salesId, LocalDate salesDate, LocalDate shippingDate,
                               Long skuId, String productName, String color, String size,
                               Integer quantity, Integer amount,
                               String shippingStatusCode, Long shippingMethodId,
                               Long paymentMethodId, boolean preorder,
                               DeliveryInfo delivery) {
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
        this.delivery = delivery;
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
    public DeliveryInfo getDelivery() { return delivery; }

    /**
     * 購入履歴に埋め込む deliveries 由来情報（フェーズ15 r5 / Step D）。
     * 注文時点で deliveries が未生成（フェーズ15以前の sales）の場合は null を返す可能性がある。
     */
    public static class DeliveryInfo {
        private final LocalDate scheduledDate;
        private final LocalDate shippedDate;
        private final LocalDate deliveredDate;
        private final String trackingCode;
        private final Long shippingStatusId;
        private final Long shippingMethodId;

        public DeliveryInfo(LocalDate scheduledDate, LocalDate shippedDate,
                            LocalDate deliveredDate, String trackingCode,
                            Long shippingStatusId, Long shippingMethodId) {
            this.scheduledDate = scheduledDate;
            this.shippedDate = shippedDate;
            this.deliveredDate = deliveredDate;
            this.trackingCode = trackingCode;
            this.shippingStatusId = shippingStatusId;
            this.shippingMethodId = shippingMethodId;
        }

        public LocalDate getScheduledDate() { return scheduledDate; }
        public LocalDate getShippedDate() { return shippedDate; }
        public LocalDate getDeliveredDate() { return deliveredDate; }
        public String getTrackingCode() { return trackingCode; }
        public Long getShippingStatusId() { return shippingStatusId; }
        public Long getShippingMethodId() { return shippingMethodId; }
    }
}
