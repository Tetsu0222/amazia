package com.example.order.controller;

import com.example.market.customer.filter.MarketSessionAuthFilter;
import com.example.order.dto.PurchaseHistoryItem;
import com.example.order.service.GetMyPurchaseHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 購入履歴 API（GET /api/customer/orders）。
 *
 * 認証: MarketSession（Cookie 経由）。
 * CSRF: GET なので MarketCsrfFilter は素通し。
 *
 * 命名規約: docs/ai_context/operation_logs_naming.md
 *   action      : (取得系のため operation_logs には記録しない)
 *   screen_name : market.purchase_history.list
 *   api_name    : GET /api/customer/orders
 */
@RestController
@RequestMapping("/api/customer/orders")
public class GetMyPurchaseHistoryController {

    private final GetMyPurchaseHistoryService service;

    public GetMyPurchaseHistoryController(GetMyPurchaseHistoryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<PurchaseHistoryItem>> list(HttpServletRequest httpReq) {
        Long customerId = (Long) httpReq.getAttribute(MarketSessionAuthFilter.ATTR_CUSTOMER_ID);
        if (customerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "session required");
        }
        return ResponseEntity.ok(service.list(customerId));
    }
}
