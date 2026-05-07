package com.example.inventory.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Check;
import java.time.LocalDateTime;

/**
 * 商品×倉庫の現在在庫（フェーズ15 r5 / RRRR-1 / RRRR-2）。
 *
 * <p>並行運用書き込み正本：入荷・販売・返品復元のすべての経路から
 * {@code InventorySyncService.applyDelta} 経由で同期更新される。
 * 読み取り正本は phase14 r2 で完全移行されるまで {@code products.stock} のまま。
 *
 * <p>命名: 既存 {@code com.example.inventory} パッケージに
 * {@code GetInventoryService}（SKU 横断の在庫一覧）が存在するため、
 * 用途を明確に区別する目的で複数形 {@code Inventories} を採用。
 * 本 Entity は {@code inventories} テーブル（product × warehouse）を表現する。
 */
@Entity
@Table(
    name = "inventories",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_inventories_product_warehouse",
        columnNames = {"product_id", "warehouse_id"}
    )
)
@Check(constraints = "quantity >= 0")
public class Inventories {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
