package com.example.inquiry.controller;

import com.example.inquiry.dto.MarketCreateInquiryRequest;
import com.example.inquiry.service.CreateInquiryService;
import com.example.market.customer.filter.MarketSessionAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Market 顧客の問い合わせ新規作成（フェーズ18）。
 *
 * <p>{@code POST /api/customer/inquiries}（MarketSession Cookie + CSRF）。
 */
@RestController
@RequestMapping("/api/customer/inquiries")
public class MarketCreateInquiryController {

    private final CreateInquiryService service;

    public MarketCreateInquiryController(CreateInquiryService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, Long>> handle(
            @Valid @RequestBody MarketCreateInquiryRequest body,
            HttpServletRequest req) {
        Long inquiryId = service.create(body, requireCustomerId(req));
        return ResponseEntity.ok(Map.of("id", inquiryId));
    }

    private Long requireCustomerId(HttpServletRequest req) {
        Long customerId = (Long) req.getAttribute(MarketSessionAuthFilter.ATTR_CUSTOMER_ID);
        if (customerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "session required");
        }
        return customerId;
    }
}
