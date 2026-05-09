package com.example.notice.service;

import com.example.notice.entity.Notice;
import com.example.notice.repository.NoticeRepository;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * お知らせ論理削除（DELETE /api/notices/{id}）。
 *
 * <p>{@code deleted_at = NOW()} のみを更新。関連 {@code notice_reads} は維持する
 * （参照履歴 / CASCADE DELETE 不採用 / 設計書 §機能詳細）。
 * 既に削除済の場合は HTTP 410 Gone を返す。
 */
@Service
@Transactional
public class DeleteNoticeService {

    private final NoticeRepository noticeRepository;
    private final OperationLogRepository operationLogRepository;

    public DeleteNoticeService(NoticeRepository noticeRepository,
                               OperationLogRepository operationLogRepository) {
        this.noticeRepository = noticeRepository;
        this.operationLogRepository = operationLogRepository;
    }

    public void delete(Long id, Long userId) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "notice not found"));
        if (notice.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.GONE, "notice already deleted");
        }
        notice.setDeletedAt(LocalDateTime.now());
        noticeRepository.saveAndFlush(notice);

        OperationLog log = new OperationLog();
        log.setUserId(userId);
        log.setAction("delete_notice");
        log.setTargetType("notices");
        log.setTargetId(id);
        log.setScreenName("console.notice.list");
        log.setApiName("DELETE /api/notices/" + id);
        log.setComment("件名：" + notice.getSubject());
        operationLogRepository.save(log);
    }
}
