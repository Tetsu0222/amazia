package com.example.notice.controller;

import com.example.notice.dto.CreateNoticeRequest;
import com.example.notice.dto.NoticeConsoleDto;
import com.example.notice.service.CreateNoticeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * お知らせ新規作成（POST /api/notices）。
 *
 * <p>actor の users.id は X-User-Id ヘッダで受け取る（既存 RegisterInboundController と同方式）。
 * Console JWT 検証は Console（Laravel）側で完了している前提で、Core は X-User-Id 値を信頼する。
 */
@RestController
@RequestMapping("/api/notices")
public class CreateNoticeController {

    private final CreateNoticeService service;

    public CreateNoticeController(CreateNoticeService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<NoticeConsoleDto> create(@Valid @RequestBody CreateNoticeRequest request,
                                                   @RequestHeader("X-User-Id") Long userId) {
        NoticeConsoleDto created = service.create(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
