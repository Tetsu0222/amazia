package com.example.batch.repository;

import com.example.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * フェーズ17 Step 3-3: SalesReconciliationJob 専用クエリ。
 *
 * <p>{@code inbounds - sales(成立分) + sales_return(REFUNDED)} を商品ごとに再計算し、
 * 倉庫合算した {@code inventories} と突合する。設計書 §3.1 ③ R-3 の SQL を H2 / MySQL 両対応で実装。
 */
public interface SalesReconciliationRepository extends JpaRepository<Product, Long> {

    /**
     * 戻り値: {@code [Number productId, Number currentQty, Number expectedQty]}.
     * 整合性が取れている行も含めて全商品を返す（不一致だけでなく経過観察のため目視可能にする）。
     * Service 側で {@code current != expected} を抽出する。
     */
    @Query(nativeQuery = true, value = """
            SELECT
              p.id AS product_id,
              COALESCE((SELECT SUM(i.quantity) FROM inventories i
                        WHERE i.product_id = p.id
                          AND i.warehouse_id IN (:warehouseIds)), 0) AS current_qty,
              ( COALESCE((SELECT SUM(inb.quantity) FROM inbounds inb
                          WHERE inb.product_id = p.id
                            AND inb.warehouse_id IN (:warehouseIds)), 0)
              - COALESCE((SELECT SUM(s.quantity)
                            FROM sales s
                            JOIN product_skus ps ON ps.id = s.sku_id
                            LEFT JOIN deliveries d ON d.sales_id = s.id
                           WHERE ps.product_id = p.id
                             AND ( s.is_preorder = false
                                OR (s.is_preorder = true AND d.shipped_date IS NOT NULL))), 0)
              + COALESCE((SELECT SUM(sr.quantity)
                            FROM sales_return sr
                            JOIN sales s ON sr.sales_id = s.id
                            JOIN product_skus ps ON ps.id = s.sku_id
                           WHERE ps.product_id = p.id
                             AND sr.status = 'REFUNDED'), 0)
              ) AS expected_qty
            FROM products p
            """)
    List<Object[]> findReconciliationRows(@Param("warehouseIds") List<Long> warehouseIds);
}
