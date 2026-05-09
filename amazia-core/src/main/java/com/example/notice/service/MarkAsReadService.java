package com.example.notice.service;

import com.example.notice.entity.NoticeRead;
import com.example.notice.repository.NoticeReadRepository;
import com.example.notice.repository.NoticeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * お知らせ既読登録（POST /api/customer/notices/{id}/read）。
 *
 * <p>冪等：同一 (notice_id, market_customer_id) の重複登録は何もしない（H2 / MySQL 互換のため
 * exists 確認 → false なら INSERT 方式を採用 / 計画書 §2-3-7）。
 *
 * <p>公開期間外 / 論理削除済 / 存在しない notice_id は HTTP 404。
 */
@Service
@Transactional
public class MarkAsReadService {

    private final NoticeRepository noticeRepository;
    private final NoticeReadRepository readRepository;

    public MarkAsReadService(NoticeRepository noticeRepository,
                             NoticeReadRepository readRepository) {
        this.noticeRepository = noticeRepository;
        this.readRepository = readRepository;
    }

    public void markAsRead(Long noticeId, Long marketCustomerId) {
        LocalDateTime now = LocalDateTime.now();
        noticeRepository.findByIdActiveAt(noticeId, now)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "notice not found"));

        if (readRepository.existsByNoticeIdAndMarketCustomerId(noticeId, marketCustomerId)) {
            return; // 冪等
        }

        NoticeRead record = new NoticeRead();
        record.setNoticeId(noticeId);
        record.setMarketCustomerId(marketCustomerId);
        record.setReadAt(now);
        readRepository.save(record);
    }
}
