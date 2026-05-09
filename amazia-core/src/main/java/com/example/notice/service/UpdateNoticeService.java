package com.example.notice.service;

import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import com.example.notice.dto.AuthorDto;
import com.example.notice.dto.NoticeConsoleDto;
import com.example.notice.dto.UpdateNoticeRequest;
import com.example.notice.entity.Notice;
import com.example.notice.entity.NoticeCategory;
import com.example.notice.repository.NoticeCategoryRepository;
import com.example.notice.repository.NoticeRepository;
import com.example.notice.validator.NoticePeriodValidator;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * お知らせ編集（PUT /api/notices/{id}）。
 *
 * <p>論理削除済（deleted_at NOT NULL）の場合は HTTP 410 Gone を返す。
 * R19-3 / R19-4：operation_logs への記録は本 Service で行う。
 */
@Service
@Transactional
public class UpdateNoticeService {

    private final NoticeRepository noticeRepository;
    private final NoticeCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final OperationLogRepository operationLogRepository;
    private final NoticePeriodValidator periodValidator;
    private final NoticePublishStateResolver publishStateResolver;
    private final int subjectMaxLength;
    private final int bodyMaxLength;

    public UpdateNoticeService(NoticeRepository noticeRepository,
                               NoticeCategoryRepository categoryRepository,
                               UserRepository userRepository,
                               OperationLogRepository operationLogRepository,
                               NoticePeriodValidator periodValidator,
                               NoticePublishStateResolver publishStateResolver,
                               @Value("${amazia.notice.subject.max-length}") int subjectMaxLength,
                               @Value("${amazia.notice.body.max-length}") int bodyMaxLength) {
        this.noticeRepository = noticeRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.operationLogRepository = operationLogRepository;
        this.periodValidator = periodValidator;
        this.publishStateResolver = publishStateResolver;
        this.subjectMaxLength = subjectMaxLength;
        this.bodyMaxLength = bodyMaxLength;
    }

    public NoticeConsoleDto update(Long id, UpdateNoticeRequest req, Long userId) {
        validateLength(req.subject(), req.body());
        periodValidator.validate(req.publishStart(), req.publishEnd());

        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "notice not found"));
        if (notice.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.GONE, "notice is deleted");
        }
        NoticeCategory category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "category not found: " + req.categoryId()));
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "actor not found: " + userId));

        notice.setSubject(req.subject());
        notice.setCategoryId(category.getId());
        notice.setBody(req.body());
        notice.setPublishStart(req.publishStart());
        notice.setPublishEnd(req.publishEnd());
        Notice saved = noticeRepository.saveAndFlush(notice);

        recordOperationLog("update_notice", "console.notice.edit",
                "PUT /api/notices/" + id, id, userId, "件名：" + saved.getSubject());

        AuthorDto author = userRepository.findById(saved.getAuthorId())
                .map(u -> new AuthorDto(u.getId(), u.getName()))
                .orElse(new AuthorDto(actor.getId(), actor.getName()));
        return NoticeConsoleDto.fromEntity(saved, category, author,
                publishStateResolver.resolve(saved, LocalDateTime.now()));
    }

    private void validateLength(String subject, String body) {
        if (subject != null && subject.length() > subjectMaxLength) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "subject exceeds max length: " + subjectMaxLength);
        }
        if (body != null && body.length() > bodyMaxLength) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "body exceeds max length: " + bodyMaxLength);
        }
    }

    private void recordOperationLog(String action, String screenName, String apiName,
                                    Long noticeId, Long userId, String comment) {
        OperationLog log = new OperationLog();
        log.setUserId(userId);
        log.setAction(action);
        log.setTargetType("notices");
        log.setTargetId(noticeId);
        log.setScreenName(screenName);
        log.setApiName(apiName);
        log.setComment(comment);
        operationLogRepository.save(log);
    }
}
