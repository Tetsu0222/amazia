package com.example.market.customer.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Market セッション Cookie のビルダー（X-3 で導入した HTTPS フラグ思想に揃える）。
 *
 * 環境変数で切替:
 *   - MARKET_COOKIE_SECURE      : 既定 false（HTTP 直 IP 運用時のフォールバック）。本番 HTTPS 経由は true。
 *   - MARKET_COOKIE_DOMAIN      : 既定 空（未設定）。本番は {@code www.amazia-portfolio.dedyn.io}。
 *   - MARKET_SESSION_TTL_SECONDS: 既定 1800（30 分 = 設計書 §3.2）。
 *
 * Path は {@code /} 固定（顧客 API と SPA の両方で送出されるよう）。
 */
@Component
public class MarketSessionCookieFactory {

    private final boolean secure;
    private final String domain;
    private final long ttlSeconds;

    public MarketSessionCookieFactory(
            @Value("${market.cookie.secure:false}") boolean secure,
            @Value("${market.cookie.domain:}") String domain,
            @Value("${market.session.ttl-seconds:1800}") long ttlSeconds) {
        this.secure = secure;
        this.domain = domain;
        this.ttlSeconds = ttlSeconds;
    }

    public ResponseCookie issue(String sessionId) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(MarketSessionAuthFilter.COOKIE_NAME, sessionId)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(ttlSeconds));
        if (domain != null && !domain.isBlank()) {
            b.domain(domain);
        }
        return b.build();
    }

    public ResponseCookie expire() {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(MarketSessionAuthFilter.COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO);
        if (domain != null && !domain.isBlank()) {
            b.domain(domain);
        }
        return b.build();
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }
}
