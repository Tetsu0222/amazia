package com.example.sku.repository;

import com.example.sku.entity.ProductSkuPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProductSkuPriceRepository extends JpaRepository<ProductSkuPrice, Long> {

    Optional<ProductSkuPrice> findBySkuId(Long skuId);

    /**
     * フェーズ17 Step 5.5-1a: 現行価格（{@code is_active = TRUE}）を 1 件返す。
     * 履歴は物理削除しない運用のため、active のみフィルタする。
     */
    Optional<ProductSkuPrice> findFirstBySkuIdAndIsActiveTrue(Long skuId);

    /**
     * フェーズ17 Step 5.5-1f: SKU 価格履歴の取得（{@code start_date DESC}）。
     * Console UI の「履歴」ブロックで使用する。
     */
    List<ProductSkuPrice> findBySkuIdOrderByStartDateDescIdDesc(Long skuId);

    List<ProductSkuPrice> findBySkuIdIn(List<Long> skuIds);

    /**
     * フェーズ17 Step 3-6 / ApplyScheduledPricesJob 用：
     * 指定 SKU の現行アクティブ価格を一括で非アクティブ化（履歴化）する。
     *
     * <p>{@code end_date} は呼び出し側が「{@code apply_date - 1日}」を渡す前提。
     * 価格レコードは物理削除しないため、過去履歴は {@code is_active = FALSE} で残る。
     *
     * @return 非アクティブ化した行数（通常は 1）
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProductSkuPrice p set p.isActive = false, p.endDate = :endDate "
            + "where p.skuId = :skuId and p.isActive = true")
    int deactivateActive(@Param("skuId") Long skuId,
                         @Param("endDate") LocalDate endDate);
}
