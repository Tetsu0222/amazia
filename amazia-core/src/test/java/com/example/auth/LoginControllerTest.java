package com.example.auth;

import com.example.auth.entity.Role;
import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import com.example.auth.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import com.example.shared.config.TestAwsConfig;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestAwsConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class LoginControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;

    @Value("${refresh-cookie.path}") String refreshCookiePath;
    @Value("${refresh-cookie.secure}") boolean refreshCookieSecure;
    @Value("${refresh-cookie.domain:}") String refreshCookieDomain;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private User createUser(String email, String rawPassword, boolean active, int failedAttempts, LocalDateTime lockedUntil) {
        Role role = roleRepository.findByCode("admin").orElseThrow();
        User user = new User();
        user.setEmployeeId("EMP_TEST_" + System.nanoTime());
        user.setEmail(email);
        user.setName("テストユーザー");
        user.setPasswordHash(encoder.encode(rawPassword));
        user.setRole(role);
        user.setActiveFlag(active);
        user.setFailedAttempts(failedAttempts);
        user.setLockedUntil(lockedUntil);
        return userRepository.save(user);
    }

    // --- 正常系 ---

    @Test
    void 有効なメールとパスワードでログインすると200とアクセストークンが返ること() throws Exception {
        createUser("valid@example.com", "Pass@1234", true, 0, null);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"valid@example.com\",\"password\":\"Pass@1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.role").value("admin"));
    }

    @Test
    void ログイン成功後にfailedAttemptsが0にリセットされること() throws Exception {
        User user = createUser("reset@example.com", "Pass@1234", true, 3, null);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"reset@example.com\",\"password\":\"Pass@1234\"}"))
                .andExpect(status().isOk());

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(0, updated.getFailedAttempts());
    }

    @Test
    void ログイン成功後にリフレッシュトークンがCookieにセットされること() throws Exception {
        createUser("cookie@example.com", "Pass@1234", true, 0, null);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"cookie@example.com\",\"password\":\"Pass@1234\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true));
    }

    @Test
    void リフレッシュトークンCookieのPathSecureDomainがconfig値どおりに反映されること() throws Exception {
        createUser("cookieattr@example.com", "Pass@1234", true, 0, null);

        var resultActions = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"cookieattr@example.com\",\"password\":\"Pass@1234\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().path("refresh_token", refreshCookiePath))
                .andExpect(cookie().secure("refresh_token", refreshCookieSecure));

        if (!refreshCookieDomain.isBlank()) {
            resultActions.andExpect(cookie().domain("refresh_token", refreshCookieDomain));
        }
    }

    // --- 異常系: 認証失敗 ---

    @Test
    void 存在しないメールアドレスで401が返ること() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@example.com\",\"password\":\"Pass@1234\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void パスワード不一致で401かつfailedAttemptsがインクリメントされること() throws Exception {
        User user = createUser("wrong@example.com", "Pass@1234", true, 0, null);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"wrong@example.com\",\"password\":\"WrongPass\"}"))
                .andExpect(status().isUnauthorized());

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(1, updated.getFailedAttempts());
    }

    @Test
    void パスワード不一致を4回繰り返してもロックされないこと() throws Exception {
        User user = createUser("lock4@example.com", "Pass@1234", true, 0, null);

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"lock4@example.com\",\"password\":\"Wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(4, updated.getFailedAttempts());
        assertNull(updated.getLockedUntil());
    }

    @Test
    void パスワード不一致を5回繰り返すとロックされること() throws Exception {
        User user = createUser("lock5@example.com", "Pass@1234", true, 0, null);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"lock5@example.com\",\"password\":\"Wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(5, updated.getFailedAttempts());
        assertNotNull(updated.getLockedUntil());
        assertTrue(updated.getLockedUntil().isAfter(LocalDateTime.now()));
    }

    // --- 異常系: アカウント状態 ---

    @Test
    void activeFlagがfalseのユーザーは403が返ること() throws Exception {
        createUser("inactive@example.com", "Pass@1234", false, 0, null);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"inactive@example.com\",\"password\":\"Pass@1234\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ロック中のユーザーは423が返ること() throws Exception {
        createUser("locked@example.com", "Pass@1234", true, 5, LocalDateTime.now().plusMinutes(10));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"locked@example.com\",\"password\":\"Pass@1234\"}"))
                .andExpect(status().isLocked());
    }

    @Test
    void ロック期限切れのユーザーは正常認証できること() throws Exception {
        createUser("expired@example.com", "Pass@1234", true, 5, LocalDateTime.now().minusMinutes(1));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"expired@example.com\",\"password\":\"Pass@1234\"}"))
                .andExpect(status().isOk());
    }

    // --- 環境・設定 ---

    @Test
    void JWTのアクセストークンがJWT形式であること() throws Exception {
        createUser("jwt@example.com", "Pass@1234", true, 0, null);

        String body = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"jwt@example.com\",\"password\":\"Pass@1234\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("accessToken"));
        String token = body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
        assertEquals(3, token.split("\\.").length);
    }
}
