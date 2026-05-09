package com.example.notice.dto;

import com.example.notice.entity.Notice;
import com.example.notice.entity.NoticeCategory;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * Console 用お知らせ DTO（フェーズ19 r2 / R19-11）。
 *
 * <p>本クラスは {@code author} と {@code deletedAt} を含み、Console 専用。
 * Market への流出は DTO クラスを分離することでコンパイル時保証する。
 *
 * <p>{@code publishState} は Service で算出（"未公開" / "公開中" / "終了" / "削除済"）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NoticeConsoleDto(
        Long id,
        String subject,
        NoticeCategoryDto category,
        String body,
        LocalDateTime publishStart,
        LocalDateTime publishEnd,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt,
        AuthorDto author,
        String publishState
) {

    public static NoticeConsoleDto fromEntity(Notice entity, NoticeCategory category,
                                              AuthorDto author, String publishState) {
        return new NoticeConsoleDto(
                entity.getId(),
                entity.getSubject(),
                NoticeCategoryDto.from(category),
                entity.getBody(),
                entity.getPublishStart(),
                entity.getPublishEnd(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt(),
                author,
                publishState
        );
    }
}
