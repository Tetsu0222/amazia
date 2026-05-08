package com.example.scheduledprice.controller;

import com.example.scheduledprice.entity.ProductSkuScheduledPrice;
import com.example.scheduledprice.service.RegisterScheduledSkuPriceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * フェーズ17 Step 5.5-1d（設計書 §13.5.1）：予約価格 UPSERT API。
 *
 * <p>{@code PUT /api/skus/{id}/scheduled-price}：既存 {@code is_pending = TRUE} があれば更新、
 * 無ければ新規作成。{@code applyDate} が今日より前なら {@code 422}。
 */
@RestController
@RequestMapping("/api")
public class RegisterScheduledSkuPriceController {

    private final RegisterScheduledSkuPriceService service;

    public RegisterScheduledSkuPriceController(RegisterScheduledSkuPriceService service) {
        this.service = service;
    }

    public record UpsertRequest(Integer scheduledPrice, LocalDate applyDate) {}

    @PutMapping("/skus/{id}/scheduled-price")
    public ResponseEntity<ProductSkuScheduledPrice> upsert(@PathVariable("id") Long id,
                                                           @RequestBody UpsertRequest request) {
        ProductSkuScheduledPrice saved = service.upsert(
                id,
                request != null ? request.scheduledPrice() : null,
                request != null ? request.applyDate() : null);
        return ResponseEntity.ok(saved);
    }
}
