package com.example.market.customer.filter;

import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(TestAwsConfig.class)
class MarketSessionCookieFactoryTest {

    @Autowired MarketSessionCookieFactory factory;

    @Value("${market.cookie.secure}") boolean configuredSecure;
    @Value("${market.cookie.domain:}") String configuredDomain;
    @Value("${market.session.ttl-seconds}") long configuredTtl;

    @Test
    void issueでHttpOnlySameSitePathが正しく設定されること() {
        ResponseCookie cookie = factory.issue("sample-session-id");

        assertEquals(MarketSessionAuthFilter.COOKIE_NAME, cookie.getName());
        assertEquals("sample-session-id", cookie.getValue());
        assertTrue(cookie.isHttpOnly(), "HttpOnly は常に true");
        assertEquals("Lax", cookie.getSameSite(), "SameSite=Lax 固定");
        assertEquals("/", cookie.getPath(), "Path=/ 固定");
    }

    @Test
    void issueがconfig値どおりのSecureとMaxAgeを反映すること() {
        ResponseCookie cookie = factory.issue("sid");

        assertEquals(configuredSecure, cookie.isSecure(), "Secure は market.cookie.secure に従う");
        assertEquals(configuredTtl, cookie.getMaxAge().getSeconds(),
                "MaxAge は market.session.ttl-seconds に従う");
        if (configuredDomain.isBlank()) {
            assertNull(cookie.getDomain(), "domain 未設定時は ResponseCookie.domain も null");
        } else {
            assertEquals(configuredDomain, cookie.getDomain());
        }
    }

    @Test
    void expireはMaxAgeがゼロでDomainSecureSameSiteが整合すること() {
        ResponseCookie cookie = factory.expire();

        assertEquals(MarketSessionAuthFilter.COOKIE_NAME, cookie.getName());
        assertEquals("", cookie.getValue());
        assertEquals(0, cookie.getMaxAge().getSeconds(), "MaxAge=0 で即削除");
        assertTrue(cookie.isHttpOnly());
        assertEquals("Lax", cookie.getSameSite());
        assertEquals("/", cookie.getPath());
        assertEquals(configuredSecure, cookie.isSecure());
    }
}
