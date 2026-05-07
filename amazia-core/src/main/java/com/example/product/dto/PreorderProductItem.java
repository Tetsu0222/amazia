package com.example.product.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * Console 予約管理画面向け 1 件分。
 *
 * 設計書 phase16_ui_ux_improvement.md §2-4-2「予約管理画面 表示内容」/ §6-4 検索条件拡充。
 * 抽出条件は {@code PreorderStatus = PRE_ORDER}（is_active = TRUE / 公開期間内 /
 * 予約開始日到来済み / 発売日未到来）。数量・金額は sales.is_preorder = TRUE のみで集計。
 * minPrice / maxPrice は当該商品配下の SKU 現行価格（product_sku_prices.price）の最小・最大。
 */
public class PreorderProductItem {

    private final Long productId;
    private final String productName;
    private final LocalDate preorderStartDate;
    private final LocalDate releaseDate;
    private final Long daysUntilRelease;
    private final boolean acceptPreorder;
    private final boolean isActive;
    private final long preorderQuantity;
    private final long preorderAmount;
    private final Integer minPrice;
    private final Integer maxPrice;

    public PreorderProductItem(Long productId,
                               String productName,
                               LocalDate preorderStartDate,
                               LocalDate releaseDate,
                               Long daysUntilRelease,
                               boolean acceptPreorder,
                               boolean isActive,
                               long preorderQuantity,
                               long preorderAmount,
                               Integer minPrice,
                               Integer maxPrice) {
        this.productId = productId;
        this.productName = productName;
        this.preorderStartDate = preorderStartDate;
        this.releaseDate = releaseDate;
        this.daysUntilRelease = daysUntilRelease;
        this.acceptPreorder = acceptPreorder;
        this.isActive = isActive;
        this.preorderQuantity = preorderQuantity;
        this.preorderAmount = preorderAmount;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
    }

    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public LocalDate getPreorderStartDate() { return preorderStartDate; }
    public LocalDate getReleaseDate() { return releaseDate; }
    public Long getDaysUntilRelease() { return daysUntilRelease; }
    public boolean isAcceptPreorder() { return acceptPreorder; }

    @JsonProperty("isActive")
    public boolean isActive() { return isActive; }

    public long getPreorderQuantity() { return preorderQuantity; }
    public long getPreorderAmount() { return preorderAmount; }
    public Integer getMinPrice() { return minPrice; }
    public Integer getMaxPrice() { return maxPrice; }
}
