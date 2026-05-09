package com.example.notice.controller;

import com.example.notice.service.GetNoticeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * お知らせ単件取得（GET /api/notices/{id}）。
 *
 * <p>{@code include_unpublished} / {@code include_deleted} は Console JWT のときのみ有効
 * （Market / 未認証では無視 / 設計書 §4）。
 */
@RestController
@RequestMapping("/api/notices")
public class GetNoticeController {

    private final GetNoticeService service;

    public GetNoticeController(GetNoticeService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public Object get(@PathVariable Long id,
                      @RequestParam(value = "include_unpublished", defaultValue = "false") boolean includeUnpublished,
                      @RequestParam(value = "include_deleted", defaultValue = "false") boolean includeDeleted,
                      HttpServletRequest request) {
        NoticeViewModeResolver.Resolution res = NoticeViewModeResolver.resolve(request);
        return service.getById(id, res.mode(), res.marketCustomerId(),
                includeUnpublished, includeDeleted);
    }
}
