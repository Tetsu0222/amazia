package com.example.notice.controller;

import com.example.notice.service.DeleteNoticeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * お知らせ論理削除（DELETE /api/notices/{id}）。
 */
@RestController
@RequestMapping("/api/notices")
public class DeleteNoticeController {

    private final DeleteNoticeService service;

    public DeleteNoticeController(DeleteNoticeService service) {
        this.service = service;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @RequestHeader("X-User-Id") Long userId) {
        service.delete(id, userId);
        return ResponseEntity.noContent().build();
    }
}
