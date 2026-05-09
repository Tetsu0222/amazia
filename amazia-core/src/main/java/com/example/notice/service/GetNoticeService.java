package com.example.notice.service;

import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import com.example.notice.dto.AuthorDto;
import com.example.notice.dto.NoticeConsoleDto;
import com.example.notice.dto.NoticeMarketDto;
import com.example.notice.entity.Notice;
import com.example.notice.entity.NoticeCategory;
import com.example.notice.repository.NoticeCategoryRepository;
import com.example.notice.repository.NoticeReadRepository;
import com.example.notice.repository.NoticeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * お知らせ単件取得（GET /api/notices/{id}）。
 *
 * <p>視点ごとの返却内容（設計書 §4 / R19-9 / R19-11）：
 * <ul>
 *   <li>{@link NoticeViewMode#CONSOLE}：{@link NoticeConsoleDto} を返却。include_unpublished /
 *       include_deleted パラメータの指定に従って取得対象を変える</li>
 *   <li>{@link NoticeViewMode#MARKET_AUTHED}：{@link NoticeMarketDto} を返却。{@code isRead} は
 *       Optional.of(...)。公開期間内 + 未削除のみ</li>
 *   <li>{@link NoticeViewMode#ANONYMOUS}：{@link NoticeMarketDto} を返却。{@code isRead} は
 *       Optional.empty()（キー自体を JSON に含めない）。公開期間内 + 未削除のみ</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class GetNoticeService {

    private final NoticeRepository noticeRepository;
    private final NoticeCategoryRepository categoryRepository;
    private final NoticeReadRepository readRepository;
    private final UserRepository userRepository;
    private final NoticePublishStateResolver publishStateResolver;

    public GetNoticeService(NoticeRepository noticeRepository,
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

    public Object getById(Long id, NoticeViewMode mode, Long marketCustomerId,
                          boolean includeUnpublished, boolean includeDeleted) {
        LocalDateTime now = LocalDateTime.now();
        Notice notice = resolveNotice(id, mode, now, includeUnpublished, includeDeleted);
        NoticeCategory category = categoryRepository.findById(notice.getCategoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "category not found for notice id=" + id));

        return switch (mode) {
            case CONSOLE -> toConsoleDto(notice, category, now);
            case MARKET_AUTHED -> toMarketDto(notice, category, marketCustomerId);
            case ANONYMOUS -> toMarketDto(notice, category, null);
        };
    }

    private Notice resolveNotice(Long id, NoticeViewMode mode, LocalDateTime now,
                                 boolean includeUnpublished, boolean includeDeleted) {
        if (mode == NoticeViewMode.CONSOLE) {
            // Console は include_* に従う
            Notice candidate = noticeRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "notice not found"));
            if (!includeDeleted && candidate.getDeletedAt() != null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "notice not found");
            }
            if (!includeUnpublished
                    && (now.isBefore(candidate.getPublishStart()) || now.isAfter(candidate.getPublishEnd()))) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "notice not found");
            }
            return candidate;
        }
        // Market / Anonymous は include_* を無視し常に「公開期間内 + 未削除」のみ
        return noticeRepository.findByIdActiveAt(id, now)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "notice not found"));
    }

    private NoticeConsoleDto toConsoleDto(Notice notice, NoticeCategory category, LocalDateTime now) {
        AuthorDto author = userRepository.findById(notice.getAuthorId())
                .map(this::toAuthorDto)
                .orElse(null);
        String state = publishStateResolver.resolve(notice, now);
        return NoticeConsoleDto.fromEntity(notice, category, author, state);
    }

    private NoticeMarketDto toMarketDto(Notice notice, NoticeCategory category, Long marketCustomerId) {
        Optional<Boolean> isRead = (marketCustomerId == null)
                ? Optional.empty()
                : Optional.of(readRepository.existsByNoticeIdAndMarketCustomerId(
                        notice.getId(), marketCustomerId));
        return NoticeMarketDto.fromEntity(notice, category, isRead);
    }

    private AuthorDto toAuthorDto(User user) {
        return new AuthorDto(user.getId(), user.getName());
    }
}
