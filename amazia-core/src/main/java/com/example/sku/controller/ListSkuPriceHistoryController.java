package com.example.sku.controller;

import com.example.sku.entity.ProductSkuPrice;
import com.example.sku.service.ListSkuPriceHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * フェーズ17 Step 5.5-1f（設計書 §13.5.1）：SKU 価格履歴 取得 API。
 *
 * <p>{@code GET /api/skus/{id}/prices/history}：{@code start_date DESC} で全件返す。
 * 履歴は物理削除しない運用のため、過去の {@code is_active = FALSE} レコードも含む。
 */
@RestController
@RequestMapping("/api")
public class ListSkuPriceHistoryController {

    private final ListSkuPriceHistoryService service;

    public ListSkuPriceHistoryController(ListSkuPriceHistoryService service) {
        this.service = service;
    }

    @GetMapping("/skus/{id}/prices/history")
    public ResponseEntity<List<ProductSkuPrice>> list(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.list(id));
    }
}
