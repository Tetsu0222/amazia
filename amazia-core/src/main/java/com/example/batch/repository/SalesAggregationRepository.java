package com.example.batch.repository;

import com.example.sales.entity.Sales;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * フェーズ17 Step 4-2: MonthlySalesReportJob 専用の集計リポジトリ（設計書 §3.2 ②）。
 *
 * <p>4 軸（商品 / 決済方法 / 配送方法 / 予約vs通常）＋ 総合計（全 NULL）を UNION で
 * 一括取得する。返却列は {@code [product_id, payment_method_id, shipping_method_id,
 * is_preorder, total_amount, total_quantity]}（軸が NULL 値のものは「総合計」を表す）。
 *
 * <p>NULL の Object[] 表現で返ってくるため、Service 側で {@code Number} かつ {@code null}
 * を慎重に取り扱う。
 */
public interface SalesAggregationRepository extends JpaRepository<Sales, Long> {

    /**
     * @param from 当月初日（含む）
     * @param to   当月末日（含む）
     */
    @Query(nativeQuery = true, value = """
            -- 軸 1: 商品別
            SELECT sk.product_id        AS product_id,
                   CAST(NULL AS SIGNED) AS payment_method_id,
                   CAST(NULL AS SIGNED) AS shipping_method_id,
                   CAST(NULL AS SIGNED) AS is_preorder,
                   SUM(s.amount)        AS total_amount,
                   SUM(s.quantity)      AS total_quantity
              FROM sales s
              JOIN product_skus sk ON sk.id = s.sku_id
             WHERE s.sales_date BETWEEN :from AND :to
             GROUP BY sk.product_id
            UNION ALL
            -- 軸 2: 決済方法別
            SELECT CAST(NULL AS SIGNED), s.payment_method_id,
                   CAST(NULL AS SIGNED), CAST(NULL AS SIGNED),
                   SUM(s.amount), SUM(s.quantity)
              FROM sales s
             WHERE s.sales_date BETWEEN :from AND :to
             GROUP BY s.payment_method_id
            UNION ALL
            -- 軸 3: 配送方法別
            SELECT CAST(NULL AS SIGNED), CAST(NULL AS SIGNED),
                   s.shipping_method_id, CAST(NULL AS SIGNED),
                   SUM(s.amount), SUM(s.quantity)
              FROM sales s
             WHERE s.sales_date BETWEEN :from AND :to
             GROUP BY s.shipping_method_id
            UNION ALL
            -- 軸 4: 予約 vs 通常
            SELECT CAST(NULL AS SIGNED), CAST(NULL AS SIGNED),
                   CAST(NULL AS SIGNED), CAST(s.is_preorder AS SIGNED),
                   SUM(s.amount), SUM(s.quantity)
              FROM sales s
             WHERE s.sales_date BETWEEN :from AND :to
             GROUP BY s.is_preorder
            UNION ALL
            -- 総合計（NULL 軸）
            SELECT CAST(NULL AS SIGNED), CAST(NULL AS SIGNED),
                   CAST(NULL AS SIGNED), CAST(NULL AS SIGNED),
                   SUM(s.amount), SUM(s.quantity)
              FROM sales s
             WHERE s.sales_date BETWEEN :from AND :to
            """)
    List<Object[]> aggregateMonthly(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
