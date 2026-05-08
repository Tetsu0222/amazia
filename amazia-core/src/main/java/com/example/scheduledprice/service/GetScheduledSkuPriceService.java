package com.example.scheduledprice.service;

import com.example.scheduledprice.entity.ProductSkuScheduledPrice;
import com.example.scheduledprice.repository.ProductSkuScheduledPriceRepository;
import com.example.sku.repository.ProductSkuRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * フェーズ17 Step 5.5-1c（設計書 §13.5.1）：予約価格の取得。
 *
 * <p>{@code is_pending = TRUE} の予約変更を 1 件返す。なければ {@link Optional#empty()}。
 * Controller 側は空のとき {@code 204 No Content} を返す。
 */
@Service
public class GetScheduledSkuPriceService {

    private final ProductSkuScheduledPriceRepository scheduledRepository;
    private final ProductSkuRepository skuRepository;

    public GetScheduledSkuPriceService(ProductSkuScheduledPriceRepository scheduledRepository,
                                       ProductSkuRepository skuRepository) {
        this.scheduledRepository = scheduledRepository;
        this.skuRepository = skuRepository;
    }

    public Optional<ProductSkuScheduledPrice> get(Long skuId) {
        skuRepository.findById(skuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SKUが見つかりません"));
        return scheduledRepository.findFirstBySkuIdAndIsPendingTrue(skuId);
    }
}
