package com.example.operationlog.service;

import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import com.example.operationlog.dto.AdminOperationLogItem;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Console: 管理画面向けの操作履歴取得 Service（B-6）。
 *
 * 設計書 r4 / Amazia Console §操作履歴。
 * operation_logs を取得し、users と JOIN して {@link AdminOperationLogItem} に整形する。
 *
 * `screen_name` / `api_name` / `action` で絞り込み可能（NULL は無視）。
 * 並び順は createdAt DESC, id DESC（同時刻採番のタイブレーク）。
 */
@Service
public class ListOperationLogService {

    private final OperationLogRepository operationLogRepository;
    private final UserRepository userRepository;

    public ListOperationLogService(OperationLogRepository operationLogRepository,
                                   UserRepository userRepository) {
        this.operationLogRepository = operationLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminOperationLogItem> list(String screenName, String apiName, String action) {
        // 空文字は NULL 扱い（フロントから空のクエリパラメータが来た場合のガード）
        String s = isBlank(screenName) ? null : screenName;
        String a = isBlank(apiName)    ? null : apiName;
        String act = isBlank(action)   ? null : action;

        List<OperationLog> logs = operationLogRepository.search(s, a, act);
        if (logs.isEmpty()) return List.of();

        Set<Long> userIds = logs.stream().map(OperationLog::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> userMap.put(u.getId(), u));

        return logs.stream().map(log -> {
            User user = userMap.get(log.getUserId());
            return new AdminOperationLogItem(
                    log.getId(),
                    log.getUserId(),
                    user != null ? user.getName() : null,
                    log.getAction(),
                    log.getTargetType(),
                    log.getTargetId(),
                    log.getScreenName(),
                    log.getApiName(),
                    log.getComment(),
                    log.getCreatedAt()
            );
        }).toList();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }
}
