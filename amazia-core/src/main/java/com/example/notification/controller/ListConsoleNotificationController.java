package com.example.notification.controller;

import com.example.notification.entity.ConsoleNotification;
import com.example.notification.service.ListConsoleNotificationService;
import com.example.notification.service.ListConsoleNotificationService.PageResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * フェーズ17 Step 6-0: 通知センター取得（設計書 §13.7.1 / §13.7.2）。
 * GET /api/console/batch/notifications
 */
@RestController
@RequestMapping("/api/console/batch")
public class ListConsoleNotificationController {

    private final ListConsoleNotificationService service;

    public ListConsoleNotificationController(ListConsoleNotificationService service) {
        this.service = service;
    }

    @GetMapping("/notifications")
    public Map<String, Object> list(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(required = false) String level,
            @RequestParam(name = "tag", required = false) String tag,
            @RequestParam(name = "include_read", defaultValue = "false") boolean includeRead,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int size) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-User-Id required");
        }
        PageResult result = service.list(userId, level, tag, includeRead, offset, size);
        return toResponse(result.items(), result.total(), result.offset(), result.size());
    }

    private static Map<String, Object> toResponse(List<ConsoleNotification> items, long total,
                                                  int offset, int size) {
        List<Map<String, Object>> mapped = items.stream()
                .map(ListConsoleNotificationController::toMap).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", mapped);
        body.put("total", total);
        body.put("offset", offset);
        body.put("size", size);
        return body;
    }

    private static Map<String, Object> toMap(ConsoleNotification n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("level", n.getLevel());
        m.put("targetSubscriptionTag", n.getTargetSubscriptionTag());
        m.put("targetUserId", n.getTargetUserId());
        m.put("title", n.getTitle());
        m.put("body", n.getBody());
        m.put("readByUserId", n.getReadByUserId());
        m.put("readAt", n.getReadAt());
        m.put("sourceJob", n.getSourceJob());
        m.put("sourceBatchExecutionId", n.getSourceBatchExecutionId());
        m.put("createdAt", n.getCreatedAt());
        return m;
    }
}
