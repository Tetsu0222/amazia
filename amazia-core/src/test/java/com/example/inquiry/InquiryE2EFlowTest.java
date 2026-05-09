package com.example.inquiry;

import com.example.market.customer.entity.Customer;
import com.example.market.customer.entity.MarketSession;
import com.example.market.customer.filter.MarketSessionAuthFilter;
import com.example.market.customer.repository.CustomerRepository;
import com.example.market.customer.repository.MarketSessionRepository;
import com.example.shared.config.TestAwsConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * フェーズ18 Step 7: end-to-end フロー（Controller → Service → DB → 通知）の automated 検証。
 *
 * <p>計画書 §8 のシナリオ：
 *   Market から問い合わせ作成 → Console 一覧で取得 → ベルマーク件数増加 → 管理者が返信
 *   → Market 側で返信表示 → ステータス変更 → Market 側で完了表示。
 *
 * <p>MockMvc で Core の Console / Market Controller を直接叩き、フロー全体が緑になることを検証する。
 * MarketSession は MarketSessionAuthFilter.ATTR_CUSTOMER_ID 経由で attribute 注入する
 * （CSRF Filter は POST に対しても MarketSession 不在時は 401。テストでは認証済の attribute を直接乗せる）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class InquiryE2EFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private MarketSessionRepository marketSessionRepository;
    @Autowired private ObjectMapper objectMapper;

    private static final long ADMIN_USER_ID = 1L;

    private Long customerId;
    private MarketSession session;

    @BeforeEach
    void setUp() {
        Customer c = new Customer();
        c.setNameLast("E2E");
        c.setNameFirst("テスト");
        c.setPostalCode("100-0001");
        c.setAddress("東京都");
        c.setBirthday(LocalDate.of(1990, 1, 1));
        c.setEmail("e2e-" + System.nanoTime() + "@example.com");
        c.setPasswordHash("dummyhash");
        c.setPaymentMethod("credit_card");
        c.setActiveFlag(true);
        customerId = customerRepository.saveAndFlush(c).getId();

        session = new MarketSession();
        session.setSessionId("e2e-session-" + System.nanoTime());
        session.setCustomerId(customerId);
        session.setCsrfToken("e2e-csrf-token");
        session.setExpiresAt(LocalDateTime.now().plusHours(1));
        marketSessionRepository.saveAndFlush(session);
    }

    @Test
    void E2E_Market作成_Console一覧_返信_ステータス変更のフルフローが緑() throws Exception {
        // 1. Market 顧客が問い合わせを作成
        Map<String, Object> createBody = Map.of(
                "subject", "E2E テストの問い合わせ",
                "message", "本文 E2E"
        );
        MvcResult createRes = mockMvc.perform(post("/api/customer/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody))
                        .requestAttr(MarketSessionAuthFilter.ATTR_CUSTOMER_ID, customerId)
                        .requestAttr(MarketSessionAuthFilter.ATTR_SESSION, session)
                        .header("X-CSRF-Token", session.getCsrfToken()))
                .andExpect(status().isOk())
                .andReturn();
        Long inquiryId = objectMapper.readTree(createRes.getResponse().getContentAsString())
                .get("id").asLong();
        assertNotNull(inquiryId);

        // 2. Console ベルマーク件数が 1 以上に増えている
        MvcResult countRes = mockMvc.perform(get("/api/console/inquiries/unread-count")
                        .header("X-User-Id", String.valueOf(ADMIN_USER_ID)))
                .andExpect(status().isOk())
                .andReturn();
        long count = objectMapper.readTree(countRes.getResponse().getContentAsString())
                .get("count").asLong();
        assertTrue(count >= 1, "新規作成で未対応件数が 1 以上になるはず");

        // 3. Console 一覧に作成した問い合わせが含まれる
        mockMvc.perform(get("/api/console/inquiries")
                        .param("status", "NEW")
                        .header("X-User-Id", String.valueOf(ADMIN_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + inquiryId + ")].subject")
                        .value("E2E テストの問い合わせ"));

        // 4. Console 詳細でメッセージ 1 件（顧客の初回投稿）
        mockMvc.perform(get("/api/console/inquiries/" + inquiryId)
                        .header("X-User-Id", String.valueOf(ADMIN_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("E2E テストの問い合わせ"))
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].senderType").value("market_customer"));

        // 5. Console 管理者が通常返信（is_internal_note=false）
        Map<String, Object> replyBody = Map.of(
                "message", "ご回答します",
                "isInternalNote", false
        );
        mockMvc.perform(post("/api/console/inquiries/" + inquiryId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(replyBody))
                        .header("X-User-Id", String.valueOf(ADMIN_USER_ID)))
                .andExpect(status().isOk());

        // 6. Console 管理者が内部メモ投稿（is_internal_note=true）
        Map<String, Object> internalBody = Map.of(
                "message", "管理者間共有メモ",
                "isInternalNote", true
        );
        mockMvc.perform(post("/api/console/inquiries/" + inquiryId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(internalBody))
                        .header("X-User-Id", String.valueOf(ADMIN_USER_ID)))
                .andExpect(status().isOk());

        // 7. Console 詳細：内部メモ含めて 3 件返る
        mockMvc.perform(get("/api/console/inquiries/" + inquiryId)
                        .header("X-User-Id", String.valueOf(ADMIN_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(3));

        // 8. Market 顧客側の詳細：内部メモは除外され 2 件のみ
        MvcResult marketDetail = mockMvc.perform(get("/api/customer/inquiries/" + inquiryId)
                        .requestAttr(MarketSessionAuthFilter.ATTR_CUSTOMER_ID, customerId)
                        .requestAttr(MarketSessionAuthFilter.ATTR_SESSION, session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andReturn();
        // 内部メモが含まれていないこと
        JsonNode messages = objectMapper.readTree(marketDetail.getResponse().getContentAsString())
                .get("messages");
        for (JsonNode m : messages) {
            assertFalse(m.get("isInternalNote").asBoolean(),
                    "Market API は内部メモを返してはならない");
        }

        // 9. Console 管理者がステータスを IN_PROGRESS に変更
        Map<String, Object> statusBody = Map.of(
                "newStatus", "IN_PROGRESS",
                "reason", "確認中"
        );
        mockMvc.perform(patch("/api/console/inquiries/" + inquiryId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusBody))
                        .header("X-User-Id", String.valueOf(ADMIN_USER_ID)))
                .andExpect(status().isOk());

        // 10. Market 側で IN_PROGRESS が反映されている
        mockMvc.perform(get("/api/customer/inquiries/" + inquiryId)
                        .requestAttr(MarketSessionAuthFilter.ATTR_CUSTOMER_ID, customerId)
                        .requestAttr(MarketSessionAuthFilter.ATTR_SESSION, session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // 11. ステータスを DONE に変更 → Market 側で DONE 表示
        Map<String, Object> doneBody = Map.of("newStatus", "DONE", "reason", "完了");
        mockMvc.perform(patch("/api/console/inquiries/" + inquiryId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(doneBody))
                        .header("X-User-Id", String.valueOf(ADMIN_USER_ID)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/customer/inquiries/" + inquiryId)
                        .requestAttr(MarketSessionAuthFilter.ATTR_CUSTOMER_ID, customerId)
                        .requestAttr(MarketSessionAuthFilter.ATTR_SESSION, session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void E2E_他人のinquiryへMarketアクセスは403() throws Exception {
        // 別顧客を作成
        Customer other = new Customer();
        other.setNameLast("他人");
        other.setNameFirst("ABC");
        other.setPostalCode("100-0001");
        other.setAddress("東京都");
        other.setBirthday(LocalDate.of(1990, 1, 1));
        other.setEmail("other-" + System.nanoTime() + "@example.com");
        other.setPasswordHash("dummyhash");
        other.setPaymentMethod("credit_card");
        other.setActiveFlag(true);
        Long otherId = customerRepository.saveAndFlush(other).getId();

        MarketSession otherSession = new MarketSession();
        otherSession.setSessionId("e2e-other-" + System.nanoTime());
        otherSession.setCustomerId(otherId);
        otherSession.setCsrfToken("other-csrf");
        otherSession.setExpiresAt(LocalDateTime.now().plusHours(1));
        marketSessionRepository.saveAndFlush(otherSession);

        // customer が問い合わせ作成
        MvcResult res = mockMvc.perform(post("/api/customer/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subject", "本人の問い合わせ", "message", "本文")))
                        .requestAttr(MarketSessionAuthFilter.ATTR_CUSTOMER_ID, customerId)
                        .requestAttr(MarketSessionAuthFilter.ATTR_SESSION, session)
                        .header("X-CSRF-Token", session.getCsrfToken()))
                .andExpect(status().isOk())
                .andReturn();
        Long inquiryId = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("id").asLong();

        // 他顧客がアクセス → 403
        mockMvc.perform(get("/api/customer/inquiries/" + inquiryId)
                        .requestAttr(MarketSessionAuthFilter.ATTR_CUSTOMER_ID, otherId)
                        .requestAttr(MarketSessionAuthFilter.ATTR_SESSION, otherSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void E2E_session無し_Market_API_は401() throws Exception {
        mockMvc.perform(get("/api/customer/inquiries"))
                .andExpect(status().isUnauthorized());
    }
}
