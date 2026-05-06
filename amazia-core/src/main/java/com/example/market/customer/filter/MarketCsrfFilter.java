package com.example.market.customer.filter;

import com.example.market.customer.entity.MarketSession;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Market 顧客向け CSRF Filter（自作）。
 *
 * 設計書 phase13 §3.3 に従い、Spring Security の {@code CsrfFilter} は使わず、
 * セッションテーブルの {@code csrf_token} とリクエストヘッダ {@code X-CSRF-Token} を比較する。
 *
 * 検証対象:
 *   - {@code /api/customer/} 配下の **状態変更系メソッド**（POST / PUT / PATCH / DELETE）
 *   - GET / HEAD / OPTIONS は素通し
 *   - ログイン前提の API のみが対象。ログイン未要求の API（CSRF トークン取得、ログイン、登録、メール重複チェック等）は
 *     {@link #EXCLUDED_PATHS} で除外する。
 *
 * 失敗時の扱い: 401/403 を直接返さず {@code 403 Forbidden} を返す（CSRF 失敗は認証の問題ではなく権限の問題）。
 *
 * 注: {@code @Component} で登録しないこと。理由は {@link MarketSessionAuthFilter} と同じ。
 *     SecurityConfig 内で new して SecurityFilterChain にのみ組み込む。
 */
public class MarketCsrfFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-CSRF-Token";

    private static final String PROTECTED_PREFIX = "/api/customer/";

    /** CSRF 検証から除外するパス（ログイン前にもアクセスする API）。 */
    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "/api/customer/csrf-token",
            "/api/customer/login",
            "/api/customer/register",
            "/api/customer/email-availability",
            "/api/customer/postal-addresses",
            "/api/customer/password/reset",
            "/api/customer/password/reset/confirm"
    );

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!shouldValidate(request)) {
            chain.doFilter(request, response);
            return;
        }

        MarketSession session = (MarketSession) request.getAttribute(MarketSessionAuthFilter.ATTR_SESSION);
        if (session == null) {
            // 未ログインで保護対象を叩いた場合: 認証 Filter は属性を立てないため、ここで 401 ではなく 403 を返す。
            // 401/403 の使い分け:
            //  - 認証 Filter 自体は 401 を出さない（API 仕様で permitAll の経路もあるため）
            //  - CSRF Filter は「ログイン状態かつ CSRF トークン未一致」を 403 とする一方、
            //    「ログイン状態が無い」は 401 として明確に区別する。
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "session required");
            return;
        }

        String header = request.getHeader(HEADER_NAME);
        if (header == null || header.isBlank() || !constantTimeEquals(header, session.getCsrfToken())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "invalid csrf token");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean shouldValidate(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith(PROTECTED_PREFIX)) return false;
        if (SAFE_METHODS.contains(request.getMethod())) return false;
        if (EXCLUDED_PATHS.contains(path)) return false;
        return true;
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
