package com.example.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Console からのお知らせ編集リクエスト（フェーズ19 r2）。
 *
 * <p>項目は CreateNoticeRequest と同じだが、編集での意図を明示するため別クラスとして分離する。
 * 値の解釈・補完は Console 側で完了している前提。
 */
public record UpdateNoticeRequest(
        @NotBlank
        @Size(min = 1, max = 255, message = "subject: 1〜255 文字")
        String subject,

        @NotNull
        Long categoryId,

        @NotBlank
        @Size(min = 1, max = 10000, message = "body: 1〜10000 文字")
        String body,

        @NotNull
        LocalDateTime publishStart,

        @NotNull
        LocalDateTime publishEnd
) {
}
