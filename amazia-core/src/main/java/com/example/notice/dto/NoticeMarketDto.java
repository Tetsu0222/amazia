package com.example.notice.dto;

import com.example.notice.entity.Notice;
import com.example.notice.entity.NoticeCategory;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Market 用お知らせ DTO（フェーズ19 r2 / R19-11）。
 *
 * <p>本クラスは {@code author} フィールドを持たない（コンパイル時保証で投稿者情報の漏洩防止）。
 * {@code isRead} は会員セッション時のみ {@code Optional.of(...)}、未認証時は {@code Optional.empty()}
 * とすることで、{@link JsonInclude.Include#NON_ABSENT} によりキー自体を JSON から省略する（R19-9）。
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record NoticeMarketDto(
        Long id,
        String subject,
        NoticeCategoryDto category,
        String body,
        LocalDateTime publishStart,
        LocalDateTime publishEnd,
        LocalDateTime updatedAt,
        Optional<Boolean> isRead
) {

    public static NoticeMarketDto fromEntity(Notice entity, NoticeCategory category, Optional<Boolean> isRead) {
        return new NoticeMarketDto(
                entity.getId(),
                entity.getSubject(),
                NoticeCategoryDto.from(category),
                entity.getBody(),
                entity.getPublishStart(),
                entity.getPublishEnd(),
                entity.getUpdatedAt(),
                isRead
        );
    }
}
