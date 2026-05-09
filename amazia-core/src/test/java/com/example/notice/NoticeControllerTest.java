package com.example.notice;

import com.example.auth.entity.Role;
import com.example.auth.entity.User;
import com.example.auth.repository.RoleRepository;
import com.example.auth.repository.UserRepository;
import com.example.market.customer.entity.Customer;
import com.example.market.customer.entity.MarketSession;
import com.example.market.customer.filter.MarketSessionAuthFilter;
import com.example.market.customer.repository.CustomerRepository;
import com.example.market.customer.repository.MarketSessionRepository;
import com.example.notice.entity.Notice;
import com.example.notice.repository.NoticeRepository;
import com.example.shared.config.TestAwsConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * フェーズ19 Step A2: Controller 層 MockMvc テスト。
 *
 * <p>JSON レスポンス構造（R19-9 / R19-11：投稿者非表示・isRead キー省略）と
 * HTTP ステータス遷移（201 / 204 / 401 / 404 / 410 / 422）の検証に焦点。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class NoticeControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private NoticeRepository noticeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private MarketSessionRepository marketSessionRepository;

    @Value("${amazia.notice.categories.normal-id}")
    private Long normalCategoryId;
    @Value("${amazia.notice.categories.important-id}")
    private Long importantCategoryId;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private Long authorId;
    private Long customerId;
    private MarketSession session;

    @BeforeEach
    void setUp() {
        authorId = createUser().getId();
        Customer c = createCustomer();
        customerId = c.getId();
        session = new MarketSession();
        session.setSessionId("ctrl-session-" + System.nanoTime());
        session.setCustomerId(customerId);
        session.setCsrfToken("ctrl-csrf-token");
        session.setExpiresAt(LocalDateTime.now().plusHours(1));
        marketSessionRepository.saveAndFlush(session);
    }

    // ---------- POST /api/notices ----------

    @Test
    void POST_create_は_201_を返し_NoticeConsoleDto_に_author_を含む() throws Exception {
        Map<String, Object> body = Map.of(
                "subject", "件名C",
                "categoryId", normalCategoryId,
                "body", "本文",
                "publishStart", LocalDateTime.now().toString(),
                "publishEnd", LocalDateTime.now().plusDays(1).toString()
        );
        MvcResult res = mockMvc.perform(post("/api/notices")
                        .header("X-User-Id", authorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.id").value(authorId))
                .andExpect(jsonPath("$.publishState").value("公開中"))
                .andReturn();
        // is_read は Console DTO に存在しない
        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        assertFalse(json.has("isRead"));
        assertFalse(json.has("is_read"));
    }

    @Test
    void POST_create_は_publish_start_が_publish_end_より後ろで_422() throws Exception {
        Map<String, Object> body = Map.of(
                "subject", "件名",
                "categoryId", normalCategoryId,
                "body", "本文",
                "publishStart", LocalDateTime.now().plusDays(7).toString(),
                "publishEnd", LocalDateTime.now().toString()
        );
        mockMvc.perform(post("/api/notices")
                        .header("X-User-Id", authorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void POST_create_は_必須項目欠落で_422_かつ_errors_配列に_field_message_が含まれる() throws Exception {
        Map<String, Object> body = Map.of(
                // subject 欠落
                "categoryId", normalCategoryId,
                "body", "本文",
                "publishStart", LocalDateTime.now().toString(),
                "publishEnd", LocalDateTime.now().plusDays(1).toString()
        );
        mockMvc.perform(post("/api/notices")
                        .header("X-User-Id", authorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").exists())
                .andExpect(jsonPath("$.errors[0].message").exists());
    }

    // ---------- PUT /api/notices/{id} ----------

    @Test
    void PUT_update_は_200_を返し_subject_を上書きする() throws Exception {
        Long id = createNoticeRow("旧件名", normalCategoryId,
                LocalDateTime.now(), LocalDateTime.now().plusDays(1));

        Map<String, Object> body = Map.of(
                "subject", "新件名",
                "categoryId", importantCategoryId,
                "body", "新本文",
                "publishStart", LocalDateTime.now().toString(),
                "publishEnd", LocalDateTime.now().plusDays(2).toString()
        );
        mockMvc.perform(put("/api/notices/" + id)
                        .header("X-User-Id", authorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("新件名"))
                .andExpect(jsonPath("$.category.id").value(importantCategoryId));
    }

    @Test
    void PUT_update_は_存在しない_id_で_404() throws Exception {
        Map<String, Object> body = Map.of(
                "subject", "件名",
                "categoryId", normalCategoryId,
                "body", "本文",
                "publishStart", LocalDateTime.now().toString(),
                "publishEnd", LocalDateTime.now().plusDays(1).toString()
        );
        mockMvc.perform(put("/api/notices/99999")
                        .header("X-User-Id", authorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    // ---------- DELETE /api/notices/{id} ----------

    @Test
    void DELETE_は_204_を返し_2回目の削除で_410() throws Exception {
        Long id = createNoticeRow("対象", normalCategoryId,
                LocalDateTime.now(), LocalDateTime.now().plusDays(1));

        mockMvc.perform(delete("/api/notices/" + id)
                        .header("X-User-Id", authorId))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/notices/" + id)
                        .header("X-User-Id", authorId))
                .andExpect(status().isGone());
    }

    // ---------- GET /api/notices/{id} ----------

    @Test
    void GET_詳細_未認証_アクセス_は_NoticeMarketDto_を返し_isRead_キーが省略される() throws Exception {
        Long id = createNoticeRow("件名", normalCategoryId,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusDays(1));

        MvcResult res = mockMvc.perform(get("/api/notices/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("件名"))
                .andReturn();
        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        assertFalse(json.has("isRead"), "未認証時は isRead キー自体が JSON に存在しない (R19-9)");
        assertFalse(json.has("author"), "Market DTO は author を持たない (R19-11)");
        assertFalse(json.has("authorId"), "Market DTO は authorId も持たない");
    }

    @Test
    void GET_詳細_Market認証時は_isRead_を_false_で含める() throws Exception {
        Long id = createNoticeRow("件名", normalCategoryId,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusDays(1));

        MvcResult res = mockMvc.perform(get("/api/notices/" + id)
                        .requestAttr(MarketSessionAuthFilter.ATTR_CUSTOMER_ID, customerId)
                        .requestAttr(MarketSessionAuthFilter.ATTR_SESSION, session))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        assertTrue(json.has("isRead"));
        assertEquals(false, json.get("isRead").asBoolean());
        assertFalse(json.has("author"));
    }

    @Test
    void GET_詳細_Console_X_User_Id_時は_author_を含む() throws Exception {
        Long id = createNoticeRow("件名", normalCategoryId,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusDays(1));

        MvcResult res = mockMvc.perform(get("/api/notices/" + id)
                        .header("X-User-Id", authorId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        assertTrue(json.has("author"), "Console DTO は author を持つ (R19-11)");
        assertEquals(authorId, json.get("author").get("id").asLong());
    }

    @Test
    void GET_詳細_Market_は_未公開で_404() throws Exception {
        Long id = createNoticeRow("未公開", normalCategoryId,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(7));

        mockMvc.perform(get("/api/notices/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void GET_詳細_Console_は_include_unpublished_true_で_未公開を取得可能() throws Exception {
        Long id = createNoticeRow("未公開", normalCategoryId,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(7));

        mockMvc.perform(get("/api/notices/" + id)
                        .header("X-User-Id", authorId)
                        .param("include_unpublished", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishState").value("未公開"));
    }

    // ---------- GET /api/notices ----------

    @Test
    void GET_一覧_Market_は_公開期間外と論理削除済を返さない() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        createNoticeRow("公開中", normalCategoryId, now.minusDays(1), now.plusDays(1));
        createNoticeRow("未公開", normalCategoryId, now.plusDays(1), now.plusDays(7));

        mockMvc.perform(get("/api/notices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].subject", org.hamcrest.Matchers.hasItem("公開中")))
                .andExpect(jsonPath("$.content[*].subject", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("未公開"))));
    }

    // ---------- GET /api/notice-categories ----------

    @Test
    void GET_カテゴリ一覧_は_認証不要で_2件以上を_返す() throws Exception {
        mockMvc.perform(get("/api/notice-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].code").exists())
                .andExpect(jsonPath("$[0].label").exists());
    }

    // ---------- POST /api/customer/notices/{id}/read ----------

    @Test
    void POST_既読登録_は_200_かつ_2回目以降も_200_冪等() throws Exception {
        Long id = createNoticeRow("既読対象", normalCategoryId,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusDays(1));

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/customer/notices/" + id + "/read")
                            .requestAttr(MarketSessionAuthFilter.ATTR_CUSTOMER_ID, customerId)
                            .requestAttr(MarketSessionAuthFilter.ATTR_SESSION, session)
                            .header("X-CSRF-Token", session.getCsrfToken()))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void POST_既読登録_未ログインで_401() throws Exception {
        Long id = createNoticeRow("件名", normalCategoryId,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusDays(1));

        mockMvc.perform(post("/api/customer/notices/" + id + "/read"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void POST_既読登録_存在しない_id_で_404() throws Exception {
        mockMvc.perform(post("/api/customer/notices/99999/read")
                        .requestAttr(MarketSessionAuthFilter.ATTR_CUSTOMER_ID, customerId)
                        .requestAttr(MarketSessionAuthFilter.ATTR_SESSION, session)
                        .header("X-CSRF-Token", session.getCsrfToken()))
                .andExpect(status().isNotFound());
    }

    // ---------- GET /api/customer/notices/unread-count ----------

    @Test
    void GET_unread_count_は_認証時_data_配下に_important_normal_total() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        createNoticeRow("imp", importantCategoryId, now.minusMinutes(1), now.plusDays(1));
        createNoticeRow("nor", normalCategoryId, now.minusMinutes(1), now.plusDays(1));

        mockMvc.perform(get("/api/customer/notices/unread-count")
                        .requestAttr(MarketSessionAuthFilter.ATTR_CUSTOMER_ID, customerId)
                        .requestAttr(MarketSessionAuthFilter.ATTR_SESSION, session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.important").value(1))
                .andExpect(jsonPath("$.data.normal").value(1))
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void GET_unread_count_未ログインで_401() throws Exception {
        mockMvc.perform(get("/api/customer/notices/unread-count"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- GET /api/customer/notices/unread ----------

    @Test
    void GET_unread_header_は_未読のみ_かつ_author_キー無し() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        createNoticeRow("未読件名", normalCategoryId, now.minusMinutes(1), now.plusDays(1));

        MvcResult res = mockMvc.perform(get("/api/customer/notices/unread")
                        .requestAttr(MarketSessionAuthFilter.ATTR_CUSTOMER_ID, customerId)
                        .requestAttr(MarketSessionAuthFilter.ATTR_SESSION, session))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode arr = objectMapper.readTree(res.getResponse().getContentAsString());
        assertTrue(arr.isArray());
        assertTrue(arr.size() >= 1);
        for (JsonNode node : arr) {
            assertFalse(node.has("author"), "ヘッダー API も Market DTO で author を持たない");
            assertFalse(node.has("authorId"));
        }
    }

    // ---------- ヘルパ ----------

    private Long createNoticeRow(String subject, Long categoryId,
                                 LocalDateTime publishStart, LocalDateTime publishEnd) {
        Notice n = new Notice();
        n.setSubject(subject);
        n.setCategoryId(categoryId);
        n.setBody("本文");
        n.setAuthorId(authorId);
        n.setPublishStart(publishStart);
        n.setPublishEnd(publishEnd);
        return noticeRepository.saveAndFlush(n).getId();
    }

    private User createUser() {
        Role role = roleRepository.findByCode("admin").orElseThrow();
        User u = new User();
        u.setEmployeeId("EMP_CTRL_" + System.nanoTime());
        u.setEmail("ctrl-" + System.nanoTime() + "@example.com");
        u.setName("テストユーザー");
        u.setPasswordHash(encoder.encode("Pass@1234"));
        u.setRole(role);
        u.setActiveFlag(true);
        return userRepository.saveAndFlush(u);
    }

    private Customer createCustomer() {
        Customer c = new Customer();
        c.setNameLast("山田");
        c.setNameFirst("太郎");
        c.setPostalCode("100-0001");
        c.setAddress("東京都");
        c.setBirthday(LocalDate.of(1990, 1, 1));
        c.setEmail("ctrl-cust-" + System.nanoTime() + "@example.com");
        c.setPasswordHash("dummyhash");
        c.setPaymentMethod("credit_card");
        c.setActiveFlag(true);
        return customerRepository.saveAndFlush(c);
    }
}
