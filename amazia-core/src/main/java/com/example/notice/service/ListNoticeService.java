package com.example.notice.service;

import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import com.example.notice.dto.AuthorDto;
import com.example.notice.dto.NoticeConsoleDto;
import com.example.notice.dto.NoticeMarketDto;
import com.example.notice.entity.Notice;
import com.example.notice.entity.NoticeCategory;
import com.example.notice.entity.NoticeRead;
import com.example.notice.repository.NoticeCategoryRepository;
import com.example.notice.repository.NoticeReadRepository;
import com.example.notice.repository.NoticeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * お知らせ一覧取得（GET /api/notices）。
 *
 * <p>並び順は {@code category_id ASC, publish_start DESC, id DESC}（設計書 §機能詳細）。
 * Market 視点は公開期間内 + 未削除のみ、Console 視点は include_unpublished / include_deleted に従う。
 *
 * <p>N+1 を避けるため category と is_read は一覧取得後に一括問合せ（findAllById）で解決する
 * （Hibernate の {@code @EntityGraph} ではなく明示制御）。
 */
@Service
@Transactional(readOnly = true)
public class ListNoticeService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;

    private final NoticeRepository noticeRepository;
    private final NoticeCategoryRepository categoryRepository;
    private final NoticeReadRepository readRepository;
    private final UserRepository userRepository;
    private final NoticePublishStateResolver publishStateResolver;

    public ListNoticeService(NoticeRepository noticeRepository,
                             NoticeCategoryRepository categoryRepository,
                             NoticeReadRepository readRepository,
                             UserRepository userRepository,
                             NoticePublishStateResolver publishStateResolver) {
        this.noticeRepository = noticeRepository;
        this.categoryRepository = categoryRepository;
        this.readRepository = readRepository;
        this.userRepository = userRepository;
        this.publishStateResolver = publishStateResolver;
    }

    public Page<?> list(NoticeViewMode mode, Long marketCustomerId,
                        Long categoryId,
                        boolean includeUnpublished, boolean includeDeleted,
                        int page, int perPage) {
        int sanitizedPerPage = Math.min(Math.max(perPage, 1), MAX_PAGE_SIZE);
        int sanitizedPage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(sanitizedPage, sanitizedPerPage);
        LocalDateTime now = LocalDateTime.now();

        Page<Notice> notices = (mode == NoticeViewMode.CONSOLE)
                ? noticeRepository.searchForConsole(now, categoryId, includeUnpublished, includeDeleted, pageable)
                : noticeRepository.searchActive(now, categoryId, pageable);

        Map<Long, NoticeCategory> categoryMap = loadCategoryMap(notices.getContent());
        Map<Long, Boolean> readMap = (mode == NoticeViewMode.MARKET_AUTHED && marketCustomerId != null)
                ? loadReadMap(notices.getContent(), marketCustomerId)
                : Map.of();
        Map<Long, AuthorDto> authorMap = (mode == NoticeViewMode.CONSOLE)
                ? loadAuthorMap(notices.getContent())
                : Map.of();

        return notices.map(n -> toDto(n, mode, marketCustomerId, categoryMap, readMap, authorMap, now));
    }

    private Object toDto(Notice notice, NoticeViewMode mode, Long marketCustomerId,
                         Map<Long, NoticeCategory> categoryMap,
                         Map<Long, Boolean> readMap,
                         Map<Long, AuthorDto> authorMap,
                         LocalDateTime now) {
        NoticeCategory category = categoryMap.get(notice.getCategoryId());
        return switch (mode) {
            case CONSOLE -> NoticeConsoleDto.fromEntity(
                    notice, category, authorMap.get(notice.getAuthorId()),
                    publishStateResolver.resolve(notice, now));
            case MARKET_AUTHED -> NoticeMarketDto.fromEntity(notice, category,
                    Optional.of(readMap.getOrDefault(notice.getId(), false)));
            case ANONYMOUS -> NoticeMarketDto.fromEntity(notice, category, Optional.empty());
        };
    }

    private Map<Long, NoticeCategory> loadCategoryMap(List<Notice> notices) {
        if (notices.isEmpty()) return Map.of();
        Set<Long> ids = notices.stream().map(Notice::getCategoryId).collect(Collectors.toSet());
        return categoryRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(NoticeCategory::getId, c -> c));
    }

    private Map<Long, Boolean> loadReadMap(List<Notice> notices, Long marketCustomerId) {
        if (notices.isEmpty()) return Map.of();
        Set<Long> noticeIds = notices.stream().map(Notice::getId).collect(Collectors.toSet());
        Set<Long> readIds = new HashSet<>(
                readRepository.findReadNoticeIdsByCustomer(marketCustomerId, noticeIds));
        Map<Long, Boolean> map = new HashMap<>();
        for (Notice n : notices) {
            map.put(n.getId(), readIds.contains(n.getId()));
        }
        return map;
    }

    private Map<Long, AuthorDto> loadAuthorMap(List<Notice> notices) {
        if (notices.isEmpty()) return Map.of();
        Set<Long> ids = notices.stream().map(Notice::getAuthorId).collect(Collectors.toSet());
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> new AuthorDto(u.getId(), u.getName())));
    }
}
