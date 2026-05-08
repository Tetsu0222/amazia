package com.example.scheduledprice.service;

import com.example.scheduledprice.entity.ProductSkuScheduledPrice;
import com.example.scheduledprice.repository.ProductSkuScheduledPriceRepository;
import com.example.sku.repository.ProductSkuRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

/**
 * フェーズ17 Step 5.5-1d（設計書 §13.5.1）：予約価格の UPSERT。
 *
 * <p>挙動：
 * <ul>
 *   <li>{@code is_pending = TRUE} の既存行があれば値を更新（{@code scheduledPrice} / {@code applyDate}）</li>
 *   <li>無ければ新規 INSERT</li>
 *   <li>{@code applyDate} は {@code today} 以降必須。過去日は {@code 422 Unprocessable Entity}</li>
 *   <li>{@code scheduledPrice} は 0 以上必須（DB CHECK と整合）</li>
 * </ul>
 */
@Service
public class RegisterScheduledSkuPriceService {

    private final ProductSkuScheduledPriceRepository scheduledRepository;
    private final ProductSkuRepository skuRepository;

    public RegisterScheduledSkuPriceService(ProductSkuScheduledPriceRepository scheduledRepository,
                                            ProductSkuRepository skuRepository) {
        this.scheduledRepository = scheduledRepository;
        this.skuRepository = skuRepository;
    }

    @Transactional
    public ProductSkuScheduledPrice upsert(Long skuId, Integer scheduledPrice, LocalDate applyDate) {
        skuRepository.findById(skuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SKUが見つかりません"));

        if (scheduledPrice == null || scheduledPrice < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "scheduledPrice は 0 以上で指定してください");
        }
        if (applyDate == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "applyDate は必須です");
        }
        if (applyDate.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "applyDate は今日以降で指定してください");
        }

        Optional<ProductSkuScheduledPrice> existing =
                scheduledRepository.findFirstBySkuIdAndIsPendingTrue(skuId);
        ProductSkuScheduledPrice entity = existing.orElseGet(ProductSkuScheduledPrice::new);
        entity.setSkuId(skuId);
        entity.setScheduledPrice(scheduledPrice);
        entity.setApplyDate(applyDate);
        entity.setIsPending(Boolean.TRUE);
        entity.setAppliedAt(null);
        return scheduledRepository.save(entity);
    }
}
