package com.example.inquiry.controller;

import com.example.inquiry.dto.ConsoleUpdateInquiryStatusRequest;
import com.example.inquiry.dto.InquiryStatusMutationContext;
import com.example.inquiry.service.UpdateInquiryStatusService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Console 管理者によるステータス変更（フェーズ18）。
 *
 * <p>{@code PATCH /api/console/inquiries/{id}/status}
 */
@RestController
@RequestMapping("/api/console/inquiries")
public class UpdateInquiryStatusController {

    private final UpdateInquiryStatusService service;

    public UpdateInquiryStatusController(UpdateInquiryStatusService service) {
        this.service = service;
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> handle(
            @PathVariable Long id,
            @Valid @RequestBody ConsoleUpdateInquiryStatusRequest body,
            @RequestHeader(value = "X-User-Id", required = false) Long actingUserId) {
        if (actingUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-User-Id required");
        }
        InquiryStatusMutationContext ctx = new InquiryStatusMutationContext(
                id, body.newStatus(), body.reason(), actingUserId);
        service.update(ctx);
        return ResponseEntity.ok(Map.of("id", id, "status", body.newStatus()));
    }
}
