package com.example.notice.service;

import com.example.notice.entity.Notice;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Console 用 publish_state（"未公開" / "公開中" / "終了" / "削除済"）算出ヘルパ。
 * Service / Controller の重複を避けるため Component 化。
 */
@Component
public class NoticePublishStateResolver {

    public static final String STATE_UNPUBLISHED = "未公開";
    public static final String STATE_ACTIVE = "公開中";
    public static final String STATE_ENDED = "終了";
    public static final String STATE_DELETED = "削除済";

    public String resolve(Notice notice, LocalDateTime now) {
        if (notice.getDeletedAt() != null) {
            return STATE_DELETED;
        }
        if (now.isBefore(notice.getPublishStart())) {
            return STATE_UNPUBLISHED;
        }
        if (now.isAfter(notice.getPublishEnd())) {
            return STATE_ENDED;
        }
        return STATE_ACTIVE;
    }
}
