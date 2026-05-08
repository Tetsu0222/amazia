package com.example.scheduledprice.controller;

import com.example.scheduledprice.entity.ProductSkuScheduledPrice;
import com.example.scheduledprice.service.GetScheduledSkuPriceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * フェーズ17 Step 5.5-1c（設計書 §13.5.1）：予約価格 取得 API。
 *
 * <p>{@code GET /api/skus/{id}/scheduled-price}：{@code is_pending = TRUE} の予約変更を 1 件返す。
 * 無ければ {@code 204 No Content}。
 */
@RestController
@RequestMapping("/api")
public class GetScheduledSkuPriceController {

    private final GetScheduledSkuPriceService service;

    public GetScheduledSkuPriceController(GetScheduledSkuPriceService service) {
        this.service = service;
    }

    @GetMapping("/skus/{id}/scheduled-price")
    public ResponseEntity<ProductSkuScheduledPrice> get(@PathVariable("id") Long id) {
        Optional<ProductSkuScheduledPrice> hit = service.get(id);
        return hit.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
