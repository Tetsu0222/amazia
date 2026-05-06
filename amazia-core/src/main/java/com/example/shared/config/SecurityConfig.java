package com.example.shared.config;

import com.example.market.customer.filter.MarketCsrfFilter;
import com.example.market.customer.filter.MarketSessionAuthFilter;
import com.example.market.customer.repository.MarketSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Market 顧客向けセッション認証 Filter（Cookie 検証 + sliding 延長）。
     * Filter Bean を直接登録すると servlet container にも自動登録され二重実行になるため、
     * SecurityFilterChain にだけ組み込む目的で Bean メソッドではなく
     * @{@link Bean} 化せずインスタンス化し、{@link #filterChain} 内のみで使う。
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           MarketSessionRepository sessionRepository,
                                           @Value("${market.session.ttl-seconds:1800}") long sessionTtlSeconds) throws Exception {
        // 設計書 §3.3 に従い Spring Security の CsrfFilter は使わず自作 Filter を差し込む。
        MarketSessionAuthFilter sessionAuthFilter = new MarketSessionAuthFilter(sessionRepository, sessionTtlSeconds);
        MarketCsrfFilter csrfFilter = new MarketCsrfFilter();

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            // 順序: SessionAuth → Csrf → UsernamePasswordAuthenticationFilter
            // addFilterBefore は登録順を保つ（先に登録したフィルタが先に実行される）。
            .addFilterBefore(sessionAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(csrfFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
