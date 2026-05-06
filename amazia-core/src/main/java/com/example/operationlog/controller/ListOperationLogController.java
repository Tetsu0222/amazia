package com.example.operationlog.controller;

import com.example.operationlog.dto.AdminOperationLogItem;
import com.example.operationlog.service.ListOperationLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 操作履歴一覧 API（GET /api/operation-logs）。
 *
 * 想定呼び出し元: Amazia Console（JWT 検証経由）。
 *
 * クエリパラメータ:
 *   - screenName: 部分一致（例: "console.sales_return"）
 *   - apiName   : 部分一致（例: "/api/sales-returns"）
 *   - action    : 完全一致（例: "approve_sales_return"）
 *
 * 命名規約: docs/ai_context/operation_logs_naming.md
 *   action      : (取得系のため operation_logs には記録しない)
 *   screen_name : console.operation_log.list
 *   api_name    : GET /api/operation-logs
 */
@RestController
@RequestMapping("/api")
public class ListOperationLogController {

    private final ListOperationLogService service;

    public ListOperationLogController(ListOperationLogService service) {
        this.service = service;
    }

    @GetMapping("/operation-logs")
    public ResponseEntity<List<AdminOperationLogItem>> list(
            @RequestParam(value = "screenName", required = false) String screenName,
            @RequestParam(value = "apiName",    required = false) String apiName,
            @RequestParam(value = "action",     required = false) String action) {
        return ResponseEntity.ok(service.list(screenName, apiName, action));
    }
}
