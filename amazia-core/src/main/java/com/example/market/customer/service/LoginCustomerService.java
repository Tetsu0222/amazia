package com.example.market.customer.service;

import com.example.market.customer.dto.LoginCustomerRequest;
import com.example.market.customer.dto.LoginCustomerResponse;
import com.example.market.customer.entity.Customer;
import com.example.market.customer.entity.MarketSession;
import com.example.market.customer.filter.MarketSessionCookieFactory;
import com.example.market.customer.repository.CustomerRepository;
import com.example.market.customer.repository.MarketSessionRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class LoginCustomerService {

    private static final Logger log = LoggerFactory.getLogger(LoginCustomerService.class);

    private final CustomerRepository customerRepository;
    private final MarketSessionRepository sessionRepository;
    private final BCryptPasswordEncoder encoder;
    private final MarketSessionCookieFactory cookieFactory;
    private final int maxFailedAttempts;
    private final int lockMinutes;
    private final long sessionTtlSeconds;

    public LoginCustomerService(CustomerRepository customerRepository,
                                MarketSessionRepository sessionRepository,
                                BCryptPasswordEncoder encoder,
                                MarketSessionCookieFactory cookieFactory,
                                @Value("${market.account.max-failed-attempts:5}") int maxFailedAttempts,
                                @Value("${market.account.lock-minutes:5}") int lockMinutes,
                                @Value("${market.session.ttl-seconds:1800}") long sessionTtlSeconds) {
        this.customerRepository = customerRepository;
        this.sessionRepository = sessionRepository;
        this.encoder = encoder;
        this.cookieFactory = cookieFactory;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockMinutes = lockMinutes;
        this.sessionTtlSeconds = sessionTtlSeconds;
    }

    /**
     * 注意: あえて {@code @Transactional} を付けない。
     * 失敗時に {@code customerRepository.save()} で failedAttempts を保存した直後に
     * {@code ResponseStatusException} を投げる必要があるが、メソッド境界の Spring 管理トランザクションで
     * RuntimeException ロールバック規定により失敗カウンタも巻き戻されてしまう。
     * Spring Data JPA の {@code save()} 自体が REQUIRED トランザクションを開いてその場でコミットするため、
     * メソッド全体には付けず、それぞれの save() に任せる方針（既存 {@link com.example.auth.service.LoginService} と同じ）。
     */
    public LoginCustomerResponse login(LoginCustomerRequest req, HttpServletResponse response) {
        Optional<Customer> opt = customerRepository.findByEmail(req.getEmail());
        if (opt.isEmpty()) {
            log.info("market login failed (unknown email) email={}", maskEmail(req.getEmail()));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
        }

        Customer customer = opt.get();

        if (!customer.isActiveFlag()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "inactive account");
        }
        if (customer.getLockedUntil() != null && customer.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.LOCKED, "account locked");
        }

        if (!encoder.matches(req.getPassword(), customer.getPasswordHash())) {
            int attempts = customer.getFailedAttempts() + 1;
            customer.setFailedAttempts(attempts);
            if (attempts >= maxFailedAttempts) {
                customer.setLockedUntil(LocalDateTime.now().plusMinutes(lockMinutes));
            }
            customerRepository.save(customer);
            log.info("market login failed (bad password) email={} attempts={}", maskEmail(customer.getEmail()), attempts);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
        }

        customer.setFailedAttempts(0);
        customer.setLockedUntil(null);
        customerRepository.save(customer);

        // セッション固定攻撃対策: ログイン成功時に既存セッションを破棄して新規発行する。
        sessionRepository.deleteByCustomerId(customer.getId());

        String sessionId = UUID.randomUUID().toString().replace("-", "");
        String csrfToken = generateCsrfToken();
        MarketSession session = new MarketSession();
        session.setSessionId(sessionId);
        session.setCustomerId(customer.getId());
        session.setCsrfToken(csrfToken);
        LocalDateTime now = LocalDateTime.now();
        session.setCreatedAt(now);
        session.setLastAccessedAt(now);
        session.setExpiresAt(now.plusSeconds(sessionTtlSeconds));
        sessionRepository.save(session);

        ResponseCookie cookie = cookieFactory.issue(sessionId);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        log.info("market login success id={} email={}", customer.getId(), maskEmail(customer.getEmail()));

        return new LoginCustomerResponse(customer.getId(), customer.getEmail(), csrfToken);
    }

    private String generateCsrfToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(at);
        return email.charAt(0) + "***" + email.substring(at);
    }
}
