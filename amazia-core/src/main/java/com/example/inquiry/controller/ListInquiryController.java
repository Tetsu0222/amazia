package com.example.inquiry.controller;

import com.example.inquiry.dto.InquiryListFilter;
import com.example.inquiry.dto.InquiryListResponse;
import com.example.inquiry.service.ListInquiryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Console 一覧（フェーズ18）。
 *
 * <p>{@code GET /api/console/inquiries?status=&dateFrom=&dateTo=&userName=&targetType=&page=&size=}
 */
@RestController
@RequestMapping("/api/console/inquiries")
public class ListInquiryController {

    private final ListInquiryService service;

    @Value("${amazia.inquiry.page-size-console}")
    private int defaultPageSize;

    public ListInquiryController(ListInquiryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Page<InquiryListResponse>> handle(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false) String userName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        InquiryListFilter filter =
                new InquiryListFilter(status, targetType, dateFrom, dateTo, userName);
        Pageable pageable = PageRequest.of(page, size != null ? size : defaultPageSize);
        return ResponseEntity.ok(service.listForConsole(filter, pageable));
    }
}
