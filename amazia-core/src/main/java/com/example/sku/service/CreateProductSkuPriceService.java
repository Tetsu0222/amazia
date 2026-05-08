package com.example.sku.service;

import com.example.sku.entity.ProductSkuPrice;
import com.example.sku.repository.ProductSkuPriceRepository;
import com.example.sku.repository.ProductSkuRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

/**
 * フェーズ17 Step 5.5-1b（設計書 §13.5.1）：現行価格の即時登録。
 *
 * <p>仕様変更（r7）：
 * <ul>
 *   <li>従来：単一行を {@code DELETE} → 新規 {@code INSERT}</li>
 *   <li>新規：既存 {@code is_active=TRUE} 行を {@code is_active=FALSE / end_date=今日-1日} に降格
 *       した上で新規 {@code is_active=TRUE} 行を {@code INSERT} するトランザクション処理</li>
 * </ul>
 *
 * <p>履歴は物理削除しない（{@link ProductSkuPriceRepository#deactivateActive} と同じ
 * 「降格＋新規 INSERT」セマンティクスを Service 層で再現する）。
 */
@Service
public class CreateProductSkuPriceService {

    private final ProductSkuPriceRepository priceRepository;
    private final ProductSkuRepository skuRepository;

    public CreateProductSkuPriceService(ProductSkuPriceRepository priceRepository,
                                        ProductSkuRepository skuRepository) {
        this.priceRepository = priceRepository;
        this.skuRepository = skuRepository;
    }

    @Transactional
    public ProductSkuPrice create(Long skuId, ProductSkuPrice request) {
        skuRepository.findById(skuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SKUが見つかりません"));

        LocalDate today = LocalDate.now();
        // 既存 active を非アクティブ化（end_date = 今日-1日）。履歴として残す
        priceRepository.deactivateActive(skuId, today.minusDays(1));

        ProductSkuPrice newPrice = new ProductSkuPrice();
        newPrice.setSkuId(skuId);
        newPrice.setPrice(request.getPrice());
        // 即時反映なので start_date は未指定なら today
        newPrice.setStartDate(request.getStartDate() != null ? request.getStartDate() : today);
        newPrice.setEndDate(null);
        newPrice.setIsActive(Boolean.TRUE);
        return priceRepository.save(newPrice);
    }
}
