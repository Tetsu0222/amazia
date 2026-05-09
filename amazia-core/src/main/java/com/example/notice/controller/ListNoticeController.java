package com.example.notice.controller;

import com.example.notice.service.ListNoticeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * お知らせ一覧（GET /api/notices）。
 *
 * <p>ページング {@code page}（1 始まり） / {@code per_page}（最大 100、デフォルト 20）。
 * フィルタ {@code category_id} 任意。
 * Console 視点のみ {@code include_unpublished} / {@code include_deleted} を解釈する。
 */
@RestController
@RequestMapping("/api/notices")
public class ListNoticeController {

    private final ListNoticeService service;

    public ListNoticeController(ListNoticeService service) {
        this.service = service;
    }

    @GetMapping
    public Page<?> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "per_page", defaultValue = "20") int perPage,
            @RequestParam(value = "category_id", required = false) Long categoryId,
            @RequestParam(value = "include_unpublished", defaultValue = "false") boolean includeUnpublished,
            @RequestParam(value = "include_deleted", defaultValue = "false") boolean includeDeleted,
            HttpServletRequest request) {
        NoticeViewModeResolver.Resolution res = NoticeViewModeResolver.resolve(request);
        // 1 始まり page → Spring の 0 始まりに変換
        int zeroBasedPage = Math.max(page - 1, 0);
        return service.list(res.mode(), res.marketCustomerId(),
                categoryId, includeUnpublished, includeDeleted, zeroBasedPage, perPage);
    }
}
