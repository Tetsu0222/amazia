package com.example.auth;

import com.example.auth.entity.RefreshToken;
import com.example.auth.entity.Role;
import com.example.auth.entity.User;
import com.example.auth.repository.RefreshTokenRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.repository.RoleRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.example.shared.config.TestAwsConfig;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestAwsConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class RefreshTokenControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private User testUser;

    @BeforeEach
    void setUp() {
        Role role = roleRepository.findByCode("admin").orElseThrow();
        testUser = new User();
        testUser.setEmployeeId("EMP_REFRESH");
        testUser.setEmail("refresh@example.com");
        testUser.setName("リフレッシュテスト");
        testUser.setPasswordHash(encoder.encode("Pass@1234"));
        testUser.setRole(role);
        testUser.setActiveFlag(true);
        userRepository.save(testUser);
    }

    private String saveRefreshToken(String raw, LocalDateTime expiresAt, boolean revoked) {
        String hash = org.springframework.util.DigestUtils.md5DigestAsHex(raw.getBytes());
        RefreshToken token = new RefreshToken();
        token.setUser(testUser);
        token.setTokenHash(hash);
        token.setExpiresAt(expiresAt);
        token.setRevoked(revoked);
        refreshTokenRepository.save(token);
        return raw;
    }

    // --- 正常系 ---

    @Test
    void 有効なリフレッシュトークンで新しいアクセストークンが返ること() throws Exception {
        String raw = saveRefreshToken("valid-raw-token-abc", LocalDateTime.now().plusDays(14), false);

        mockMvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie("refresh_token", raw)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void トークンローテーション後に旧トークンは失効フラグが立つこと() throws Exception {
        String raw = saveRefreshToken("rotate-raw-token", LocalDateTime.now().plusDays(14), false);
        String hash = org.springframework.util.DigestUtils.md5DigestAsHex(raw.getBytes());

        mockMvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie("refresh_token", raw)))
                .andExpect(status().isOk());

        RefreshToken old = refreshTokenRepository.findByTokenHash(hash).orElseThrow();
        assertTrue(old.isRevoked());
    }

    @Test
    void 旧トークンで再試行すると401になること() throws Exception {
        String raw = saveRefreshToken("replay-raw-token", LocalDateTime.now().plusDays(14), false);

        mockMvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie("refresh_token", raw)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie("refresh_token", raw)))
                .andExpect(status().isUnauthorized());
    }

    // --- 異常系 ---

    @Test
    void Cookieにリフレッシュトークンがない場合は401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 存在しないトークン値で401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie("refresh_token", "nonexistent-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 有効期限切れのトークンで401() throws Exception {
        String raw = saveRefreshToken("expired-raw-token", LocalDateTime.now().minusMinutes(1), false);

        mockMvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie("refresh_token", raw)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokedフラグが立っているトークンで401() throws Exception {
        String raw = saveRefreshToken("revoked-raw-token", LocalDateTime.now().plusDays(14), true);

        mockMvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie("refresh_token", raw)))
                .andExpect(status().isUnauthorized());
    }
}
