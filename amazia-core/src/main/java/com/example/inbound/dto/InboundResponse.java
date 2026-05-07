package com.example.inbound.dto;

import com.example.inbound.entity.Inbound;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 入荷ヘッダのレスポンス DTO。
 */
public class InboundResponse {

    private final Long id;
    private final Long productId;
    private final Long warehouseId;
    private final Long supplierId;
    private final Integer quantity;
    private final LocalDate inboundedAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public InboundResponse(Inbound i) {
        this.id = i.getId();
        this.productId = i.getProductId();
        this.warehouseId = i.getWarehouseId();
        this.supplierId = i.getSupplierId();
        this.quantity = i.getQuantity();
        this.inboundedAt = i.getInboundedAt();
        this.createdAt = i.getCreatedAt();
        this.updatedAt = i.getUpdatedAt();
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public Long getWarehouseId() { return warehouseId; }
    public Long getSupplierId() { return supplierId; }
    public Integer getQuantity() { return quantity; }
    public LocalDate getInboundedAt() { return inboundedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
