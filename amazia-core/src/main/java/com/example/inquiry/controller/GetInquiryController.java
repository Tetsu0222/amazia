package com.example.inquiry.controller;

import com.example.inquiry.dto.InquiryDetailResponse;
import com.example.inquiry.service.GetInquiryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Console 詳細（フェーズ18）。
 *
 * <p>{@code GET /api/console/inquiries/{id}}
 * 内部メモ含めて全件返す。
 */
@RestController
@RequestMapping("/api/console/inquiries")
public class GetInquiryController {

    private final GetInquiryService service;

    public GetInquiryController(GetInquiryService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public ResponseEntity<InquiryDetailResponse> handle(@PathVariable Long id) {
        return ResponseEntity.ok(service.getForConsole(id));
    }
}
