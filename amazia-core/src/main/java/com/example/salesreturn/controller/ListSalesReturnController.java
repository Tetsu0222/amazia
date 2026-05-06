package com.example.salesreturn.controller;

import com.example.salesreturn.dto.AdminSalesReturnItem;
import com.example.salesreturn.service.ListSalesReturnService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 返品一覧 API（GET /api/sales-returns）。
 *
 * 想定呼び出し元: Amazia Console（JWT 検証経由）。
 *
 * 命名規約: docs/ai_context/operation_logs_naming.md
 *   action      : (取得系のため operation_logs には記録しない)
 *   screen_name : console.sales_returns.list
 *   api_name    : GET /api/sales-returns
 */
@RestController
@RequestMapping("/api")
public class ListSalesReturnController {

    private final ListSalesReturnService service;

    public ListSalesReturnController(ListSalesReturnService service) {
        this.service = service;
    }

    @GetMapping("/sales-returns")
    public ResponseEntity<List<AdminSalesReturnItem>> list() {
        return ResponseEntity.ok(service.list());
    }
}
