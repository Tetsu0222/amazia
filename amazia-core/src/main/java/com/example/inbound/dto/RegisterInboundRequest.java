package com.example.inbound.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/**
 * 入荷登録 API リクエスト（フェーズ15 r5 / R-3 / RRRR-5）。
 *
 * <p>{@code warehouseId} は受け取らない：UI に倉庫選択フィールドを表示せず、
 * バックエンドが {@code config('amazia.delivery.default-warehouse-id')} を自動セットする。
 */
public class RegisterInboundRequest {

    @NotNull
    @Positive
    private Long productId;

    /** 入荷ヘッダ（inbounds）と SKU 在庫増分を結ぶための SKU ID（着手時方針 #1：UI で SKU 選択） */
    @NotNull
    @Positive
    private Long skuId;

    @NotNull
    @Min(1)
    private Integer quantity;

    /** 仕入先 ID。マスタ未整備のため任意（将来 phaseX で活用） */
    private Long supplierId;

    @NotNull
    private LocalDate inboundedAt;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public LocalDate getInboundedAt() { return inboundedAt; }
    public void setInboundedAt(LocalDate inboundedAt) { this.inboundedAt = inboundedAt; }
}
