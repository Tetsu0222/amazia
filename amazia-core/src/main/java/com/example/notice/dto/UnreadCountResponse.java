package com.example.notice.dto;

/**
 * 未読数集計レスポンス DTO（GET /api/customer/notices/unread-count）。
 *
 * <p>レスポンス形：{@code { "data": { "important": N, "normal": M, "total": N+M } }}（設計書 §6）。
 * 分類が未存在のときは 0 を埋める（クライアント UX 安定化）。
 */
public record UnreadCountResponse(Data data) {

    public record Data(long important, long normal, long total) {
    }

    public static UnreadCountResponse of(long important, long normal) {
        return new UnreadCountResponse(new Data(important, normal, important + normal));
    }
}
