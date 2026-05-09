package com.example.notice.controller;

import com.example.market.customer.filter.MarketSessionAuthFilter;
import com.example.notice.service.NoticeViewMode;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Controller 用：HTTP リクエストから {@link NoticeViewMode} と関連 ID を解決するヘルパ。
 *
 * <p>判定優先順位（設計書 §4 / §5）：
 * <ol>
 *   <li>X-User-Id ヘッダあり → CONSOLE モード（Console JWT 検証は Console 側で完了済み前提）</li>
 *   <li>Market セッション認証済み（{@code MarketSessionAuthFilter#ATTR_CUSTOMER_ID} あり） → MARKET_AUTHED</li>
 *   <li>いずれも無い → ANONYMOUS</li>
 * </ol>
 */
public final class NoticeViewModeResolver {

    private NoticeViewModeResolver() { /* utility */ }

    public static Resolution resolve(HttpServletRequest request) {
        Long userId = parseLongHeader(request, "X-User-Id");
        if (userId != null) {
            return new Resolution(NoticeViewMode.CONSOLE, userId, null);
        }
        Long customerId = (Long) request.getAttribute(MarketSessionAuthFilter.ATTR_CUSTOMER_ID);
        if (customerId != null) {
            return new Resolution(NoticeViewMode.MARKET_AUTHED, null, customerId);
        }
        return new Resolution(NoticeViewMode.ANONYMOUS, null, null);
    }

    private static Long parseLongHeader(HttpServletRequest request, String header) {
        String raw = request.getHeader(header);
        if (raw == null || raw.isBlank()) return null;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record Resolution(NoticeViewMode mode, Long consoleUserId, Long marketCustomerId) {
    }
}
