package com.example.inquiry.controller;

import com.example.inquiry.dto.InquiryListResponse;
import com.example.inquiry.service.ListInquiryService;
import com.example.market.customer.filter.MarketSessionAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Market 顧客の問い合わせ一覧（自分のみ強制 / フェーズ18）。
 *
 * <p>{@code GET /api/customer/inquiries?page=&size=}
 */
@RestController
@RequestMapping("/api/customer/inquiries")
public class MarketListInquiryController {

    private final ListInquiryService service;

    @Value("${amazia.inquiry.page-size-market}")
    private int defaultPageSize;

    public MarketListInquiryController(ListInquiryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Page<InquiryListResponse>> handle(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest req) {
        Long customerId = requireCustomerId(req);
        Pageable pageable = PageRequest.of(page, size != null ? size : defaultPageSize);
        return ResponseEntity.ok(service.listForMarket(customerId, pageable));
    }

    private Long requireCustomerId(HttpServletRequest req) {
        Long customerId = (Long) req.getAttribute(MarketSessionAuthFilter.ATTR_CUSTOMER_ID);
        if (customerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "session required");
        }
        return customerId;
    }
}
