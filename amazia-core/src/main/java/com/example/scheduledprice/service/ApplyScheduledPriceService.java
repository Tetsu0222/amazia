package com.example.scheduledprice.service;

import com.example.scheduledprice.entity.ProductSkuScheduledPrice;
import com.example.scheduledprice.repository.ProductSkuScheduledPriceRepository;
import com.example.sku.entity.ProductSkuPrice;
import com.example.sku.repository.ProductSkuPriceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * フェーズ17 Step 3-6: 1 SKU 分の予約価格適用処理（設計書 §3.1 ⑥ / r7 / 実装計画 §3-6）。
 *
 * <p>Job 本体（{@link com.example.batch.job.ApplyScheduledPricesJob}）から SKU 単位で呼ばれ、
 * 1 トランザクションで「現行価格の非アクティブ化 → 新価格 INSERT → 予約レコード適用済化」を
 * アトミックに実行する。途中失敗時は SKU 単位でロールバックされ、次回バッチで再試行される。
 *
 * <p>冪等性：{@code is_pending = TRUE} の判定が鍵。本 Service は呼び出し時点で
 * {@link ProductSkuScheduledPrice#getIsPending()} が真であることを暗黙の前提とし、
 * 二重適用は Job 側のクエリ（{@code findByApplyDateLessThanEqualAndIsPendingTrue}）で防ぐ。
 */
@Service
public class ApplyScheduledPriceService {

    private final ProductSkuPriceRepository skuPriceRepository;
    private final ProductSkuScheduledPriceRepository scheduledRepository;

    public ApplyScheduledPriceService(ProductSkuPriceRepository skuPriceRepository,
                                      ProductSkuScheduledPriceRepository scheduledRepository) {
        this.skuPriceRepository = skuPriceRepository;
        this.scheduledRepository = scheduledRepository;
    }

    @Transactional
    public void applyOne(ProductSkuScheduledPrice scheduled) {
        skuPriceRepository.deactivateActive(scheduled.getSkuId(),
                scheduled.getApplyDate().minusDays(1));

        ProductSkuPrice newPrice = new ProductSkuPrice();
        newPrice.setSkuId(scheduled.getSkuId());
        newPrice.setPrice(scheduled.getScheduledPrice());
        newPrice.setStartDate(scheduled.getApplyDate());
        newPrice.setEndDate(null);
        newPrice.setIsActive(Boolean.TRUE);
        skuPriceRepository.save(newPrice);

        scheduled.setIsPending(Boolean.FALSE);
        scheduled.setAppliedAt(LocalDateTime.now());
        scheduledRepository.save(scheduled);
    }
}
