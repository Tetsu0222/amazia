package com.example.operationlog.dto;

import java.time.LocalDateTime;

/**
 * Console 操作履歴画面向け operation_logs 1 件分。
 *
 * 設計書 r4 / Amazia Console §操作履歴。
 *   - user_id / user_name（users.name JOIN）
 *   - action / target_type / target_id
 *   - screen_name / api_name
 *   - comment / created_at
 *
 * フィルタ・並び替えは Console 側で実装。
 */
public class AdminOperationLogItem {

    private final Long id;
    private final Long userId;
    private final String userName;
    private final String action;
    private final String targetType;
    private final Long targetId;
    private final String screenName;
    private final String apiName;
    private final String comment;
    private final LocalDateTime createdAt;

    public AdminOperationLogItem(Long id, Long userId, String userName,
                                 String action, String targetType, Long targetId,
                                 String screenName, String apiName,
                                 String comment, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.userName = userName;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.screenName = screenName;
        this.apiName = apiName;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public String getScreenName() { return screenName; }
    public String getApiName() { return apiName; }
    public String getComment() { return comment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
