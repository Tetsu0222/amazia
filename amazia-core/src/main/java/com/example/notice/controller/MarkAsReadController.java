package com.example.notice.controller;

import com.example.market.customer.filter.MarketSessionAuthFilter;
import com.example.notice.service.MarkAsReadService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Market 会員のお知らせ既読登録（POST /api/customer/notices/{id}/read）。
 * 会員セッション + CSRF（MarketCsrfFilter）必須。冪等。
 */
@RestController
@RequestMapping("/api/customer/notices")
public class MarkAsReadController {

    private final MarkAsReadService service;

    public MarkAsReadController(MarkAsReadService service) {
        this.service = service;
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id, HttpServletRequest request) {
        Long customerId = (Long) request.getAttribute(MarketSessionAuthFilter.ATTR_CUSTOMER_ID);
        if (customerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "session required");
        }
        service.markAsRead(id, customerId);
        return ResponseEntity.ok().build();
    }
}
