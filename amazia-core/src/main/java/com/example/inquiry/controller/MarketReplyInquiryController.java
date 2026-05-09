package com.example.inquiry.controller;

import com.example.inquiry.dto.MarketReplyInquiryRequest;
import com.example.inquiry.dto.ReplyInquiryCommand;
import com.example.inquiry.service.ReplyInquiryService;
import com.example.market.customer.filter.MarketSessionAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Market 顧客の問い合わせ返信投稿（フェーズ18）。
 *
 * <p>{@code POST /api/customer/inquiries/{id}/messages}
 * {@code is_internal_note} は DTO に持たない（RV-9）。Service 層では常に {@code false} で投入する。
 */
@RestController
@RequestMapping("/api/customer/inquiries")
public class MarketReplyInquiryController {

    private final ReplyInquiryService service;

    public MarketReplyInquiryController(ReplyInquiryService service) {
        this.service = service;
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<Map<String, Long>> handle(
            @PathVariable Long id,
            @Valid @RequestBody MarketReplyInquiryRequest body,
            HttpServletRequest req) {
        Long customerId = requireCustomerId(req);
        ReplyInquiryCommand cmd = new ReplyInquiryCommand(
                id, "market_customer", customerId, body.message(), false);
        Long messageId = service.reply(cmd);
        return ResponseEntity.ok(Map.of("id", messageId));
    }

    private Long requireCustomerId(HttpServletRequest req) {
        Long customerId = (Long) req.getAttribute(MarketSessionAuthFilter.ATTR_CUSTOMER_ID);
        if (customerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "session required");
        }
        return customerId;
    }
}
