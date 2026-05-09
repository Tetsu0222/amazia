package com.example.notice.service;

import com.example.notice.dto.NoticeMarketDto;
import com.example.notice.entity.Notice;
import com.example.notice.entity.NoticeCategory;
import com.example.notice.repository.NoticeCategoryRepository;
import com.example.notice.repository.NoticeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ヘッダー用未読お知らせ取得（GET /api/customer/notices/unread）。
 *
 * <p>未読 + 公開期間内 + 未削除を {@code amazia.notice.header.max-items}（=10）件まで返す。
 * 並び順は category_id ASC → publish_start DESC → id DESC。レスポンスは
 * {@link NoticeMarketDto} の配列で {@code author} を含まない（R19-11）。
 */
@Service
@Transactional(readOnly = true)
public class GetUnreadHeaderNoticesService {

    private final NoticeRepository noticeRepository;
    private final NoticeCategoryRepository categoryRepository;
    private final int maxItems;

    public GetUnreadHeaderNoticesService(NoticeRepository noticeRepository,
                                         NoticeCategoryRepository categoryRepository,
                                         @Value("${amazia.notice.header.max-items}") int maxItems) {
        this.noticeRepository = noticeRepository;
        this.categoryRepository = categoryRepository;
        this.maxItems = maxItems;
    }

    public List<NoticeMarketDto> findUnread(Long marketCustomerId) {
        LocalDateTime now = LocalDateTime.now();
        List<Notice> notices = noticeRepository.findUnreadHeaderNotices(
                now, marketCustomerId, PageRequest.of(0, maxItems));
        if (notices.isEmpty()) return List.of();

        Set<Long> categoryIds = notices.stream()
                .map(Notice::getCategoryId)
                .collect(Collectors.toSet());
        Map<Long, NoticeCategory> categoryMap = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(NoticeCategory::getId, c -> c));

        // 未読取得 API の返却なので isRead は常に false（ただし表示時はキー省略する仕様 / R19-9）
        return notices.stream()
                .map(n -> NoticeMarketDto.fromEntity(n, categoryMap.get(n.getCategoryId()),
                        Optional.of(false)))
                .toList();
    }
}
