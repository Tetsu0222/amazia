package com.example.inventory.repository;

import com.example.inventory.entity.Inventories;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * inventories テーブル（product × warehouse の現在在庫）リポジトリ。
 * 既存 {@code com.example.inventory.service.GetInventoryService} とは用途が異なる
 * （あちらは SKU 横断の在庫一覧）。本リポジトリは並行運用フックから使用する。
 */
public interface InventoriesRepository extends JpaRepository<Inventories, Long> {

    Optional<Inventories> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    /**
     * 並行運用の在庫減算で使用する悲観ロック付き取得（RRR-8 / RRRR-2）。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventories i where i.productId = :productId and i.warehouseId = :warehouseId")
    Optional<Inventories> findByProductIdAndWarehouseIdForUpdate(
            @Param("productId") Long productId,
            @Param("warehouseId") Long warehouseId);
}
