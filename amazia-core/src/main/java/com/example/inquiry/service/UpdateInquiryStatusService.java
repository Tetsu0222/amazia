package com.example.inquiry.service;

import com.example.inquiry.dto.InquiryStatusMutationContext;
import com.example.inquiry.entity.Inquiry;
import com.example.inquiry.exception.IllegalInquiryStatusTransitionException;
import com.example.inquiry.exception.InquiryNotFoundException;
import com.example.inquiry.repository.InquiryRepository;
import com.example.inquiry.service.notification.InquiryNotificationDispatcher;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Console 管理者によるステータス変更（フェーズ18 / I-6）。
 *
 * <p>許容遷移ルールは {@code amazia.inquiry.allowed-status-transitions} を properties から
 * 単一行 CSV 形式（{@code 旧:新1,新2;旧:新1,新2}）でパースして保持する。
 */
@Service
public class UpdateInquiryStatusService {

    private final InquiryRepository inquiryRepository;
    private final InquiryNotificationDispatcher notificationDispatcher;
    private final OperationLogRepository operationLogRepository;

    @Value("${amazia.inquiry.allowed-status-transitions}")
    private String allowedTransitionsCsv;

    @Value("${amazia.inquiry.operation-log-prefixes.status-change}")
    private String statusChangePrefix;

    private Map<String, List<String>> allowedTransitions;

    public UpdateInquiryStatusService(InquiryRepository inquiryRepository,
                                      InquiryNotificationDispatcher notificationDispatcher,
                                      OperationLogRepository operationLogRepository) {
        this.inquiryRepository = inquiryRepository;
        this.notificationDispatcher = notificationDispatcher;
        this.operationLogRepository = operationLogRepository;
    }

    @PostConstruct
    void parseAllowedTransitions() {
        Map<String, List<String>> map = new HashMap<>();
        for (String chunk : allowedTransitionsCsv.split(";")) {
            String[] kv = chunk.split(":");
            if (kv.length != 2) continue;
            String from = kv[0].trim();
            List<String> tos = Arrays.stream(kv[1].split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            map.put(from, tos);
        }
        this.allowedTransitions = Map.copyOf(map);
    }

    @Transactional
    public void update(InquiryStatusMutationContext ctx) {
        Inquiry inquiry = inquiryRepository.findById(ctx.inquiryId())
                .orElseThrow(() -> new InquiryNotFoundException(
                        "inquiry not found: " + ctx.inquiryId()));

        String oldStatus = inquiry.getStatus();
        String newStatus = ctx.newStatus();
        if (Objects.equals(oldStatus, newStatus)) {
            return; // no-op
        }

        List<String> allowed = allowedTransitions.getOrDefault(oldStatus, List.of());
        if (!allowed.contains(newStatus)) {
            throw new IllegalInquiryStatusTransitionException(
                    "transition not allowed: " + oldStatus + " -> " + newStatus);
        }

        inquiry.setStatus(newStatus);
        inquiry.setUpdatedAt(LocalDateTime.now());
        inquiryRepository.save(inquiry);

        notificationDispatcher.dispatchStatusChanged(inquiry, oldStatus, newStatus);
        recordOperationLog(ctx, oldStatus, newStatus);
    }

    private void recordOperationLog(InquiryStatusMutationContext ctx, String oldStatus, String newStatus) {
        OperationLog opLog = new OperationLog();
        opLog.setUserId(ctx.actingUserId());
        opLog.setAction("update_inquiry_status");
        opLog.setTargetType("inquiries");
        opLog.setTargetId(ctx.inquiryId());
        opLog.setScreenName("ConsoleInquiryDetailPage");
        opLog.setApiName("PATCH /api/console/inquiries/" + ctx.inquiryId() + "/status");
        StringBuilder comment = new StringBuilder();
        comment.append(statusChangePrefix)
                .append(" 旧:").append(oldStatus)
                .append(" → 新:").append(newStatus);
        if (ctx.reason() != null && !ctx.reason().isBlank()) {
            comment.append(" reason='").append(ctx.reason()).append("'");
        }
        opLog.setComment(comment.toString());
        operationLogRepository.save(opLog);
    }
}
