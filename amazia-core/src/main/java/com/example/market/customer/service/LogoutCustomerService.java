package com.example.market.customer.service;

import com.example.market.customer.filter.MarketSessionAuthFilter;
import com.example.market.customer.filter.MarketSessionCookieFactory;
import com.example.market.customer.repository.MarketSessionRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogoutCustomerService {

    private final MarketSessionRepository sessionRepository;
    private final MarketSessionCookieFactory cookieFactory;

    public LogoutCustomerService(MarketSessionRepository sessionRepository,
                                 MarketSessionCookieFactory cookieFactory) {
        this.sessionRepository = sessionRepository;
        this.cookieFactory = cookieFactory;
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String sessionId = readCookie(request);
        if (sessionId != null) {
            sessionRepository.deleteById(sessionId);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.expire().toString());
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (MarketSessionAuthFilter.COOKIE_NAME.equals(c.getName())) {
                String v = c.getValue();
                return (v == null || v.isBlank()) ? null : v;
            }
        }
        return null;
    }
}
