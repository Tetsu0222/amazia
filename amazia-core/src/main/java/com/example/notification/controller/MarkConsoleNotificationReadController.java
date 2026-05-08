package com.example.notification.controller;

import com.example.notification.service.MarkConsoleNotificationReadService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * フェーズ17 Step 6-0: Console 通知の既読化（設計書 §13.7.1）。
 * PUT /api/console/batch/notifications/{id}/read
 */
@RestController
@RequestMapping("/api/console/batch")
public class MarkConsoleNotificationReadController {

    private final MarkConsoleNotificationReadService service;

    public MarkConsoleNotificationReadController(MarkConsoleNotificationReadService service) {
        this.service = service;
    }

    @PutMapping("/notifications/{id}/read")
    public Map<String, Object> markRead(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-User-Id required");
        }
        service.markRead(id, userId);
        return Map.of("id", id, "status", "read");
    }
}
