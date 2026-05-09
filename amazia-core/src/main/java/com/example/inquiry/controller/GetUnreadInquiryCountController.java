package com.example.inquiry.controller;

import com.example.inquiry.service.GetUnreadInquiryCountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Console ベルマーク用の未対応件数取得（フェーズ18）。
 *
 * <p>{@code GET /api/console/inquiries/unread-count} → {@code { count: number }}。
 * 設計書 §6.3 真実の元（{@code inquiries.status='NEW'} の COUNT）。
 */
@RestController
@RequestMapping("/api/console/inquiries")
public class GetUnreadInquiryCountController {

    private final GetUnreadInquiryCountService service;

    public GetUnreadInquiryCountController(GetUnreadInquiryCountService service) {
        this.service = service;
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> handle() {
        return ResponseEntity.ok(Map.of("count", service.count()));
    }
}
