package com.example.sku.service;

import com.example.sku.entity.ProductSkuPrice;
import com.example.sku.repository.ProductSkuPriceRepository;
import com.example.sku.repository.ProductSkuRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * フェーズ17 Step 5.5-1f（設計書 §13.5.1）：SKU 価格履歴の取得。
 *
 * <p>{@code product_sku_prices} を {@code start_date DESC} で全件返す。Console UI の
 * 「履歴」タブで使用する（{@code is_active = TRUE} の現行行も先頭に含まれる）。
 */
@Service
public class ListSkuPriceHistoryService {

    private final ProductSkuPriceRepository priceRepository;
    private final ProductSkuRepository skuRepository;

    public ListSkuPriceHistoryService(ProductSkuPriceRepository priceRepository,
                                      ProductSkuRepository skuRepository) {
        this.priceRepository = priceRepository;
        this.skuRepository = skuRepository;
    }

    public List<ProductSkuPrice> list(Long skuId) {
        skuRepository.findById(skuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SKUが見つかりません"));
        return priceRepository.findBySkuIdOrderByStartDateDescIdDesc(skuId);
    }
}
