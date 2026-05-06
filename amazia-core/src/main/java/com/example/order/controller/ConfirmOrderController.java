package com.example.order.controller;

import com.example.market.customer.filter.MarketSessionAuthFilter;
import com.example.order.dto.ConfirmOrderRequest;
import com.example.order.dto.ConfirmOrderResponse;
import com.example.order.exception.PaymentIdConflictException;
import com.example.order.service.OrderConfirmationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 注文確定 API（POST /api/customer/orders/confirm）。
 *
 * 認証: Cookie ベース MarketSession（MarketSessionAuthFilter で customerId を取得）
 * CSRF: /api/customer/ 配下なので MarketCsrfFilter で X-CSRF-Token 検証あり（除外パスではない）
 *
 * 命名規約: docs/ai_context/operation_logs_naming.md
 *   action      : confirm_order
 *   screen_name : market.checkout.confirm
 *   api_name    : POST /api/customer/orders/confirm
 */
@RestController
@RequestMapping("/api/customer/orders")
public class ConfirmOrderController {

    private final OrderConfirmationService service;

    public ConfirmOrderController(OrderConfirmationService service) {
        this.service = service;
    }

    @PostMapping("/confirm")
    public ResponseEntity<ConfirmOrderResponse> confirm(@Valid @RequestBody ConfirmOrderRequest req,
                                                       HttpServletRequest httpReq) {
        Long customerId = (Long) httpReq.getAttribute(MarketSessionAuthFilter.ATTR_CUSTOMER_ID);
        if (customerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "session required");
        }
        return ResponseEntity.ok(new ConfirmOrderResponse(service.confirm(customerId, req)));
    }

    @ExceptionHandler(PaymentIdConflictException.class)
    public ResponseEntity<String> handlePaymentIdConflict(PaymentIdConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }
}
