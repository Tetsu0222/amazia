package com.example.notice.service;

import com.example.notice.dto.UnreadCountResponse;
import com.example.notice.repository.NoticeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 未読数集計（GET /api/customer/notices/unread-count）。
 *
 * <p>レスポンス：{@code { "data": { "important": N, "normal": M, "total": N+M } }}（設計書 §6）。
 * 未存在 category は 0 で埋める（クライアント UX 安定化）。
 */
@Service
@Transactional(readOnly = true)
public class GetUnreadCountService {

    private static final String CODE_IMPORTANT = "important";
    private static final String CODE_NORMAL = "normal";

    private final NoticeRepository noticeRepository;

    public GetUnreadCountService(NoticeRepository noticeRepository) {
        this.noticeRepository = noticeRepository;
    }

    public UnreadCountResponse count(Long marketCustomerId) {
        LocalDateTime now = LocalDateTime.now();
        List<Object[]> rows = noticeRepository.countUnreadByCategory(now, marketCustomerId);

        long important = 0L;
        long normal = 0L;
        for (Object[] row : rows) {
            String code = (String) row[0];
            long count = ((Number) row[1]).longValue();
            if (CODE_IMPORTANT.equals(code)) important = count;
            else if (CODE_NORMAL.equals(code)) normal = count;
            // 未知の code は無視（マスタ拡張時の前方互換）
        }
        return UnreadCountResponse.of(important, normal);
    }
}
