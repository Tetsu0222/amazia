package com.example.inquiry.controller;

import com.example.inquiry.dto.InquiryDetailResponse;
import com.example.inquiry.service.GetInquiryService;
import com.example.market.customer.filter.MarketSessionAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Market 顧客の問い合わせ詳細（自分のみ / フェーズ18）。
 *
 * <p>{@code GET /api/customer/inquiries/{id}}（内部メモは API レスポンスから除外）。
 */
@RestController
@RequestMapping("/api/customer/inquiries")
public class MarketGetInquiryController {

    private final GetInquiryService service;

    public MarketGetInquiryController(GetInquiryService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public ResponseEntity<InquiryDetailResponse> handle(@PathVariable Long id, HttpServletRequest req) {
        Long customerId = requireCustomerId(req);
        return ResponseEntity.ok(service.getForMarket(id, customerId));
    }

    private Long requireCustomerId(HttpServletRequest req) {
        Long customerId = (Long) req.getAttribute(MarketSessionAuthFilter.ATTR_CUSTOMER_ID);
        if (customerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "session required");
        }
        return customerId;
    }
}
