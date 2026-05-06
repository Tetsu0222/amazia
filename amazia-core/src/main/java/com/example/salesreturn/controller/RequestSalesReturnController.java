package com.example.salesreturn.controller;

import com.example.market.customer.filter.MarketSessionAuthFilter;
import com.example.salesreturn.dto.RequestSalesReturnRequest;
import com.example.salesreturn.dto.RequestSalesReturnResponse;
import com.example.salesreturn.entity.SalesReturn;
import com.example.salesreturn.service.RequestSalesReturnService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 返品申請 API（POST /api/customer/sales-returns）。
 *
 * 認証: Cookie ベース MarketSession（MarketSessionAuthFilter で customerId を取得）
 * CSRF: /api/customer/ 配下なので MarketCsrfFilter で X-CSRF-Token 検証あり
 *
 * 命名規約: docs/ai_context/operation_logs_naming.md
 *   action      : request_sales_return
 *   screen_name : market.purchase_history.request_return
 *   api_name    : POST /api/customer/sales-returns
 */
@RestController
@RequestMapping("/api/customer/sales-returns")
public class RequestSalesReturnController {

    private final RequestSalesReturnService service;

    public RequestSalesReturnController(RequestSalesReturnService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<RequestSalesReturnResponse> request(@Valid @RequestBody RequestSalesReturnRequest req,
                                                              HttpServletRequest httpReq) {
        Long customerId = (Long) httpReq.getAttribute(MarketSessionAuthFilter.ATTR_CUSTOMER_ID);
        if (customerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "session required");
        }
        SalesReturn created = service.request(customerId, req);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RequestSalesReturnResponse(created.getId(), created.getStatus()));
    }
}
