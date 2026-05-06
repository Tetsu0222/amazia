package com.example.salesreturn.controller;

import com.example.salesreturn.dto.SalesReturnResponse;
import com.example.salesreturn.entity.SalesReturn;
import com.example.salesreturn.service.RejectSalesReturnService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 返品却下 API（POST /api/sales-returns/{id}/reject）。
 *
 * 想定呼び出し元: Amazia Console（JWT 検証経由）。
 * 操作者の users.id は X-User-Id ヘッダで受け取る（既存ワークフロー API と同じ作法）。
 *
 * 命名規約: docs/ai_context/operation_logs_naming.md
 *   action      : reject_sales_return
 *   screen_name : console.sales_returns.reject
 *   api_name    : POST /api/sales-returns/{id}/reject
 */
@RestController
@RequestMapping("/api")
public class RejectSalesReturnController {

    private final RejectSalesReturnService service;

    public RejectSalesReturnController(RejectSalesReturnService service) {
        this.service = service;
    }

    @PostMapping("/sales-returns/{id}/reject")
    public ResponseEntity<SalesReturnResponse> reject(@PathVariable Long id,
                                                      @RequestHeader("X-User-Id") Long userId) {
        SalesReturn rejected = service.reject(id, userId);
        return ResponseEntity.ok(new SalesReturnResponse(rejected));
    }
}
