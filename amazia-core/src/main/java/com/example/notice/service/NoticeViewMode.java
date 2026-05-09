package com.example.notice.service;

/**
 * Service が呼び出された視点（Console / Market 認証済 / 未認証）を識別する列挙。
 *
 * <p>Controller 層で認証情報から決定し、Service / レスポンス整形に渡す。
 */
public enum NoticeViewMode {
    /** Console 社員（X-User-Id ヘッダ + 認可済み）。 */
    CONSOLE,
    /** Market 会員セッション認証済み。 */
    MARKET_AUTHED,
    /** 未認証（Market 公開閲覧）。 */
    ANONYMOUS
}
