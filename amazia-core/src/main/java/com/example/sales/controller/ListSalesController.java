package com.example.sales.controller;

import com.example.sales.dto.AdminSalesItem;
import com.example.sales.service.ListSalesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 売上一覧 API（GET /api/sales）。
 *
 * 想定呼び出し元: Amazia Console（JWT 検証経由）。
 *
 * 命名規約: docs/ai_context/operation_logs_naming.md
 *   action      : (取得系のため operation_logs には記録しない)
 *   screen_name : console.sales.list
 *   api_name    : GET /api/sales
 */
@RestController
@RequestMapping("/api")
public class ListSalesController {

    private final ListSalesService service;

    public ListSalesController(ListSalesService service) {
        this.service = service;
    }

    @GetMapping("/sales")
    public ResponseEntity<List<AdminSalesItem>> list() {
        return ResponseEntity.ok(service.list());
    }
}
