package com.example.product.controller;

import com.example.product.dto.PreorderProductItem;
import com.example.product.service.ListPreorderProductsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 予約商品一覧 API（GET /api/products/preorders）。
 *
 * 設計書 phase16_ui_ux_improvement.md §2-4-4。
 * 想定呼び出し元: Amazia Console（JWT 検証経由）。
 *
 * 命名規約: docs/ai_context/operation_logs_naming.md
 *   action      : (取得系のため operation_logs には記録しない)
 *   screen_name : console.preorder.list
 *   api_name    : GET /api/products/preorders
 */
@RestController
@RequestMapping("/api")
public class ListPreorderProductsController {

    private final ListPreorderProductsService service;

    public ListPreorderProductsController(ListPreorderProductsService service) {
        this.service = service;
    }

    @GetMapping("/products/preorders")
    public ResponseEntity<List<PreorderProductItem>> list() {
        return ResponseEntity.ok(service.list());
    }
}
