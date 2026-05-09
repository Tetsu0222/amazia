package com.example.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Console からのお知らせ新規作成リクエスト（フェーズ19 r2 / 設計書 §機能詳細）。
 *
 * <p>R19-2 / R19-5：時分秒（{@code 00:00:00} / {@code 23:59:59}）の補完は Console FormRequest 側の責務。
 * Core はリクエスト値そのままで {@code now() BETWEEN publish_start AND publish_end} を判定する。
 *
 * <p>長さ上限はプロパティ駆動（{@code amazia.notice.subject.max-length} / {@code body.max-length}）。
 * リテラル値はマジックナンバー回避のため {@code @Size} のメッセージにのみ含める形とし、
 * 上限超過の二重防御は Service 層 {@code NoticePeriodValidator} 隣の上限チェックで行う。
 */
public record CreateNoticeRequest(
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
