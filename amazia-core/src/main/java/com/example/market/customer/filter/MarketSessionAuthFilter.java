package com.example.market.customer.filter;

import com.example.market.customer.entity.MarketSession;
import com.example.market.customer.repository.MarketSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Market 顧客向けセッション認証 Filter（自作）。
 *
 * Cookie {@code MARKET_SESSION_ID} を検証し、有効なら以下を行う:
 *   1. {@code MarketSessionAuthFilter.ATTR_CUSTOMER_ID} に顧客 ID を設定
 *   2. {@code MarketSessionAuthFilter.ATTR_SESSION} にセッションそのものを設定（後段の CSRF Filter が参照）
 *   3. last_accessed_at と expires_at を sliding 延長
 *
 * Cookie が無い／セッションが見つからない／期限切れの場合は属性を設定せず、後続処理に委ねる
 * （未認証扱い。各 API Controller / SecurityConfig 側で 401 を返すかどうかを決める）。
 *
 * 注: {@code @Component} で登録しないこと。
 *     Spring Boot は {@code Filter} Bean を servlet container にも自動登録するため、
 *     SecurityFilterChain と二重登録になり {@code OncePerRequestFilter.logger} 初期化に
 *     関する NPE を引き起こす（MockMvc 構築時に表面化）。
 *     本クラスは {@code SecurityConfig} 内で明示的に new して SecurityFilterChain にのみ組み込む。
 */
public class MarketSessionAuthFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "MARKET_SESSION_ID";
    public static final String ATTR_CUSTOMER_ID = "market.customerId";
    public static final String ATTR_SESSION = "market.session";

    private final MarketSessionRepository sessionRepository;
    private final long sessionTtlSeconds;

    public MarketSessionAuthFilter(MarketSessionRepository sessionRepository,
                                   long sessionTtlSeconds) {
        this.sessionRepository = sessionRepository;
        this.sessionTtlSeconds = sessionTtlSeconds;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String sessionId = readCookie(request);
        if (sessionId != null) {
            Optional<MarketSession> opt = sessionRepository.findById(sessionId);
            if (opt.isPresent()) {
                MarketSession session = opt.get();
                LocalDateTime now = LocalDateTime.now();
                if (session.getExpiresAt().isAfter(now)) {
                    LocalDateTime newExpiresAt = now.plusSeconds(sessionTtlSeconds);
                    sessionRepository.touch(sessionId, now, newExpiresAt);
                    session.setLastAccessedAt(now);
                    session.setExpiresAt(newExpiresAt);
                    request.setAttribute(ATTR_CUSTOMER_ID, session.getCustomerId());
                    request.setAttribute(ATTR_SESSION, session);
                }
            }
        }
        chain.doFilter(request, response);
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName())) {
                String v = c.getValue();
                return (v == null || v.isBlank()) ? null : v;
            }
        }
        return null;
    }
}
