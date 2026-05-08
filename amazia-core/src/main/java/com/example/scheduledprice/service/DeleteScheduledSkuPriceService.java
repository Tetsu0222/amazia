package com.example.scheduledprice.service;

import com.example.scheduledprice.entity.ProductSkuScheduledPrice;
import com.example.scheduledprice.repository.ProductSkuScheduledPriceRepository;
import com.example.sku.repository.ProductSkuRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * フェーズ17 Step 5.5-1e（設計書 §13.5.1）：予約価格の取消し。
 *
 * <p>{@code is_pending = TRUE} のレコードを物理削除する（実装計画 §7-1 で物理削除を採用）。
 * 既に適用済（{@code is_pending = FALSE}）のレコードは履歴扱いで残し本 Service の対象外。
 */
@Service
public class DeleteScheduledSkuPriceService {

    private final ProductSkuScheduledPriceRepository scheduledRepository;
    private final ProductSkuRepository skuRepository;

    public DeleteScheduledSkuPriceService(ProductSkuScheduledPriceRepository scheduledRepository,
                                          ProductSkuRepository skuRepository) {
        this.scheduledRepository = scheduledRepository;
        this.skuRepository = skuRepository;
    }

    /**
     * 削除対象が無いときは {@code 204 No Content} 相当（Controller が判定）。
     * 削除した場合は削除した行を返す。
     */
    @Transactional
    public Optional<ProductSkuScheduledPrice> delete(Long skuId) {
        skuRepository.findById(skuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SKUが見つかりません"));

        Optional<ProductSkuScheduledPrice> pending =
                scheduledRepository.findFirstBySkuIdAndIsPendingTrue(skuId);
        pending.ifPresent(scheduledRepository::delete);
        return pending;
    }
}
