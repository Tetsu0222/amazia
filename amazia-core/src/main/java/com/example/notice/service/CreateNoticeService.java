package com.example.notice.service;

import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import com.example.notice.dto.AuthorDto;
import com.example.notice.dto.CreateNoticeRequest;
import com.example.notice.dto.NoticeConsoleDto;
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
 * お知らせ新規作成（POST /api/notices）。
 *
 * <p>R19-3 / R19-4：operation_logs への記録は Core Service が直接行う（Console は X-User-Id を渡すのみ）。
 * R19-6：category 存在 / actor 存在 / 公開期間の妥当性は Service で事前検証し、422 を返す。
 */
@Service
@Transactional
public class CreateNoticeService {

    private final NoticeRepository noticeRepository;
    private final NoticeCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final OperationLogRepository operationLogRepository;
    private final NoticePeriodValidator periodValidator;
    private final NoticePublishStateResolver publishStateResolver;
    private final int subjectMaxLength;
    private final int bodyMaxLength;

    public CreateNoticeService(NoticeRepository noticeRepository,
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

    public NoticeConsoleDto create(CreateNoticeRequest req, Long userId) {
        validateLength(req.subject(), req.body());
        periodValidator.validate(req.publishStart(), req.publishEnd());

        NoticeCategory category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "category not found: " + req.categoryId()));
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "actor not found: " + userId));

        Notice notice = new Notice();
        notice.setSubject(req.subject());
        notice.setCategoryId(category.getId());
        notice.setBody(req.body());
        notice.setAuthorId(author.getId());
        notice.setPublishStart(req.publishStart());
        notice.setPublishEnd(req.publishEnd());
        Notice saved = noticeRepository.saveAndFlush(notice);

        recordOperationLog("create_notice", "console.notice.create",
                "POST /api/notices", saved.getId(), userId, "件名：" + saved.getSubject());

        return NoticeConsoleDto.fromEntity(saved, category,
                new AuthorDto(author.getId(), author.getName()),
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
