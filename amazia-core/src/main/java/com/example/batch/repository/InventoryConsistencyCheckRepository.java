package com.example.batch.repository;

import com.example.inventory.entity.Inventories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * フェーズ17 Step 3-1: InventoryConsistencyCheckJob 専用のクエリリポジトリ。
 *
 * <p>{@link Inventories} を対象とした native query を貼り付けるためのリポジトリで、
 * 結果は Object[] で返却される（[productId, currentQty, expectedQty]）。
 * 設計書 §3.1 ① の H-2 r8 SQL を H2 / MySQL 両対応で実装する。
 */
public interface InventoryConsistencyCheckRepository extends JpaRepository<Inventories, Long> {

    /**
     * SKU TX 累積（SKU → 商品ロールアップ）と倉庫合算した現在在庫の不一致を抽出する。
     *
     * <p>戻り値の各要素は {@code [Number productId, Number currentQty, Number expectedQty]}。
     * H2 / MySQL の数値型差異を吸収するため Object[] のまま返し、Service 側で {@link Number#longValue()}
     * / {@link Number#intValue()} で取り出す。
     *
     * @param warehouseIds 集計対象の倉庫 ID 群（{@code amazia.batch.sales-reconciliation.target-warehouse-ids}）
     */
    @Query(nativeQuery = true, value = """
            WITH expected AS (
              SELECT s.product_id AS pid,
                     COALESCE(SUM(t.quantity), 0) AS expected_qty
                FROM product_skus s
                LEFT JOIN product_sku_stock_transactions t ON t.sku_id = s.id
               GROUP BY s.product_id
            ), current_inv AS (
              SELECT i.product_id AS pid,
                     COALESCE(SUM(i.quantity), 0) AS current_qty
                FROM inventories i
               WHERE i.warehouse_id IN (:warehouseIds)
               GROUP BY i.product_id
            )
            SELECT e.pid AS product_id,
                   COALESCE(c.current_qty, 0) AS current_qty,
                   e.expected_qty AS expected_qty
              FROM expected e
              LEFT JOIN current_inv c ON c.pid = e.pid
             WHERE COALESCE(c.current_qty, 0) <> e.expected_qty
            """)
    List<Object[]> findInconsistencies(@Param("warehouseIds") List<Long> warehouseIds);
}
