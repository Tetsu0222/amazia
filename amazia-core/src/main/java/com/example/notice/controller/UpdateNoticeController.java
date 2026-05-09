package com.example.notice.controller;

import com.example.notice.dto.NoticeConsoleDto;
import com.example.notice.dto.UpdateNoticeRequest;
import com.example.notice.service.UpdateNoticeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * お知らせ編集（PUT /api/notices/{id}）。
 */
@RestController
@RequestMapping("/api/notices")
public class UpdateNoticeController {

    private final UpdateNoticeService service;

    public UpdateNoticeController(UpdateNoticeService service) {
        this.service = service;
    }

    @PutMapping("/{id}")
    public NoticeConsoleDto update(@PathVariable Long id,
                                   @Valid @RequestBody UpdateNoticeRequest request,
                                   @RequestHeader("X-User-Id") Long userId) {
        return service.update(id, request, userId);
    }
}
