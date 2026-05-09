package com.example.notice.dto;

/**
 * Console 用お知らせ投稿者表示 DTO（NoticeConsoleDto に埋め込む）。
 *
 * <p>R19-11：本クラスは {@code NoticeConsoleDto} のフィールドにのみ存在し、
 * {@code NoticeMarketDto} には含まれない（コンパイル時保証）。
 */
public record AuthorDto(Long id, String name) {
}
