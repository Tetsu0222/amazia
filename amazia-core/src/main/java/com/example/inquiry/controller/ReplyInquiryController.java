package com.example.inquiry.controller;

import com.example.inquiry.dto.ConsoleReplyInquiryRequest;
import com.example.inquiry.dto.ReplyInquiryCommand;
import com.example.inquiry.service.ReplyInquiryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Console 管理者の返信投稿（フェーズ18）。
 *
 * <p>{@code POST /api/console/inquiries/{id}/messages}
 * Console 側 Laravel が JWT を解決し、{@code X-User-Id} ヘッダで管理者 ID を Pass-through する。
 */
@RestController
@RequestMapping("/api/console/inquiries")
public class ReplyInquiryController {

    private final ReplyInquiryService service;

    public ReplyInquiryController(ReplyInquiryService service) {
        this.service = service;
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<Map<String, Long>> handle(
            @PathVariable Long id,
            @Valid @RequestBody ConsoleReplyInquiryRequest body,
            @RequestHeader(value = "X-User-Id", required = false) Long actingUserId) {
        if (actingUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-User-Id required");
        }
        boolean isInternalNote = Boolean.TRUE.equals(body.isInternalNote());
        ReplyInquiryCommand cmd = new ReplyInquiryCommand(
                id, "admin_user", actingUserId, body.message(), isInternalNote);
        Long messageId = service.reply(cmd);
        return ResponseEntity.ok(Map.of("id", messageId));
    }
}
