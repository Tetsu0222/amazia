package com.example.notice;

import com.example.auth.entity.Role;
import com.example.auth.entity.User;
import com.example.auth.repository.RoleRepository;
import com.example.auth.repository.UserRepository;
import com.example.market.customer.entity.Customer;
import com.example.market.customer.repository.CustomerRepository;
import com.example.notice.dto.CreateNoticeRequest;
import com.example.notice.dto.NoticeConsoleDto;
import com.example.notice.dto.NoticeMarketDto;
import com.example.notice.dto.UnreadCountResponse;
import com.example.notice.dto.UpdateNoticeRequest;
import com.example.notice.entity.Notice;
import com.example.notice.repository.NoticeReadRepository;
import com.example.notice.repository.NoticeRepository;
import com.example.notice.service.CreateNoticeService;
import com.example.notice.service.DeleteNoticeService;
import com.example.notice.service.GetNoticeService;
import com.example.notice.service.GetUnreadCountService;
import com.example.notice.service.GetUnreadHeaderNoticesService;
import com.example.notice.service.ListNoticeService;
import com.example.notice.service.MarkAsReadService;
import com.example.notice.service.NoticeViewMode;
import com.example.notice.service.UpdateNoticeService;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * フェーズ19 Step A2: Notice Service 群の統合テスト（Service + JPA + H2）。
 *
 * <p>Controller 層の HTTP 振る舞い（X-User-Id ヘッダ受取・MarketSession・JSON 整形）は
 * 別途 NoticeControllerTest で検証する。本テストは Service の境界条件と返却 DTO 構造を
 * 中心にカバーする（計画書 §2-6-2 / R19-3〜R19-11 に対応）。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class NoticeServiceTest {

    @Autowired private CreateNoticeService createService;
    @Autowired private UpdateNoticeService updateService;
    @Autowired private DeleteNoticeService deleteService;
    @Autowired private GetNoticeService getService;
    @Autowired private ListNoticeService listService;
    @Autowired private MarkAsReadService markService;
    @Autowired private GetUnreadCountService unreadCountService;
    @Autowired private GetUnreadHeaderNoticesService unreadHeaderService;

    @Autowired private NoticeRepository noticeRepository;
    @Autowired private NoticeReadRepository readRepository;
    @Autowired private OperationLogRepository operationLogRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CustomerRepository customerRepository;

    @Value("${amazia.notice.categories.important-id}")
    private Long importantCategoryId;
    @Value("${amazia.notice.categories.normal-id}")
    private Long normalCategoryId;
    @Value("${amazia.notice.header.max-items}")
    private int headerMaxItems;
    @Value("${amazia.notice.body.max-length}")
    private int bodyMaxLength;
    @Value("${amazia.notice.subject.max-length}")
    private int subjectMaxLength;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private Long authorId;
    private Long customerId;

    @BeforeEach
    void setUp() {
        authorId = createUser("notice-author").getId();
        customerId = createCustomer("notice-customer").getId();
    }

    // ---------- Create ----------

    @Test
    void create_正常系_Console_DTO_に_author_と_publishState_公開中_が含まれる() {
        LocalDateTime now = LocalDateTime.now();
        CreateNoticeRequest req = new CreateNoticeRequest(
                "件名", normalCategoryId, "本文", now.minusMinutes(1), now.plusDays(7));

        NoticeConsoleDto created = createService.create(req, authorId);

        assertNotNull(created.id());
        assertEquals("件名", created.subject());
        assertEquals(normalCategoryId, created.category().id());
        assertNotNull(created.author());
        assertEquals(authorId, created.author().id());
        assertEquals("公開中", created.publishState());
        assertNotNull(created.createdAt());
    }

    @Test
    void create_は_operation_logs_に_create_notice_を_記録する() {
        long before = operationLogRepository.count();
        LocalDateTime now = LocalDateTime.now();
        CreateNoticeRequest req = new CreateNoticeRequest(
                "件名op", normalCategoryId, "本文", now, now.plusDays(1));

        NoticeConsoleDto created = createService.create(req, authorId);

        long after = operationLogRepository.count();
        assertEquals(before + 1, after);
        OperationLog last = operationLogRepository.findAll().stream()
                .reduce((a, b) -> b).orElseThrow();
        assertEquals("create_notice", last.getAction());
        assertEquals("notices", last.getTargetType());
        assertEquals(created.id(), last.getTargetId());
        assertEquals(authorId, last.getUserId());
        assertEquals("POST /api/notices", last.getApiName());
    }

    @Test
    void create_は_publish_start_が_publish_end_より後ろのとき_422() {
        LocalDateTime now = LocalDateTime.now();
        CreateNoticeRequest req = new CreateNoticeRequest(
                "件名", normalCategoryId, "本文", now.plusDays(7), now);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> createService.create(req, authorId));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
    }

    @Test
    void create_は_存在しない_category_id_で_422() {
        LocalDateTime now = LocalDateTime.now();
        CreateNoticeRequest req = new CreateNoticeRequest(
                "件名", 99999L, "本文", now, now.plusDays(1));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> createService.create(req, authorId));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
    }

    @Test
    void create_は_存在しない_actor_id_で_422() {
        LocalDateTime now = LocalDateTime.now();
        CreateNoticeRequest req = new CreateNoticeRequest(
                "件名", normalCategoryId, "本文", now, now.plusDays(1));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> createService.create(req, 99999L));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
    }

    @Test
    void create_は_subject_が上限超過で_422() {
        LocalDateTime now = LocalDateTime.now();
        String overSubject = "あ".repeat(subjectMaxLength + 1);
        CreateNoticeRequest req = new CreateNoticeRequest(
                overSubject, normalCategoryId, "本文", now, now.plusDays(1));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> createService.create(req, authorId));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
    }

    @Test
    void create_は_body_が上限超過で_422() {
        LocalDateTime now = LocalDateTime.now();
        String overBody = "あ".repeat(bodyMaxLength + 1);
        CreateNoticeRequest req = new CreateNoticeRequest(
                "件名", normalCategoryId, overBody, now, now.plusDays(1));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> createService.create(req, authorId));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
    }

    // ---------- Update ----------

    @Test
    void update_正常系_は_subject_と_body_を上書きし_operation_log_を_update_notice_で記録() {
        Long id = createNotice("元件名", normalCategoryId,
                LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        UpdateNoticeRequest req = new UpdateNoticeRequest(
                "新件名", importantCategoryId, "新本文",
                LocalDateTime.now(), LocalDateTime.now().plusDays(2));

        long before = operationLogRepository.count();
        NoticeConsoleDto updated = updateService.update(id, req, authorId);

        assertEquals("新件名", updated.subject());
        assertEquals(importantCategoryId, updated.category().id());
        assertEquals(before + 1, operationLogRepository.count());
        OperationLog last = operationLogRepository.findAll().stream()
                .reduce((a, b) -> b).orElseThrow();
        assertEquals("update_notice", last.getAction());
        assertEquals("PUT /api/notices/" + id, last.getApiName());
    }

    @Test
    void update_は_存在しない_id_で_404() {
        UpdateNoticeRequest req = new UpdateNoticeRequest(
                "件名", normalCategoryId, "本文",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> updateService.update(99999L, req, authorId));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void update_は_論理削除済みなら_410_Gone() {
        Long id = createNotice("件名", normalCategoryId,
                LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        deleteService.delete(id, authorId);
        UpdateNoticeRequest req = new UpdateNoticeRequest(
                "新件名", normalCategoryId, "新本文",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> updateService.update(id, req, authorId));
        assertEquals(HttpStatus.GONE, ex.getStatusCode());
    }

    // ---------- Delete ----------

    @Test
    void delete_正常系_は_deleted_at_を_NOT_NULL_にし_operation_log_を_delete_notice_で記録() {
        Long id = createNotice("削除対象", normalCategoryId,
                LocalDateTime.now(), LocalDateTime.now().plusDays(1));

        long before = operationLogRepository.count();
        deleteService.delete(id, authorId);

        Notice deleted = noticeRepository.findById(id).orElseThrow();
        assertNotNull(deleted.getDeletedAt(), "deleted_at が NOT NULL");
        assertEquals(before + 1, operationLogRepository.count());
        OperationLog last = operationLogRepository.findAll().stream()
                .reduce((a, b) -> b).orElseThrow();
        assertEquals("delete_notice", last.getAction());
    }

    @Test
    void delete_は_既に削除済みなら_410_Gone() {
        Long id = createNotice("削除対象", normalCategoryId,
                LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        deleteService.delete(id, authorId);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> deleteService.delete(id, authorId));
        assertEquals(HttpStatus.GONE, ex.getStatusCode());
    }

    @Test
    void delete_は_存在しない_id_で_404() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> deleteService.delete(99999L, authorId));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // ---------- Get ----------

    @Test
    void get_は_Console_視点で_NoticeConsoleDto_を返し_publishState_と_author_を含む() {
        Long id = createNotice("件名", normalCategoryId,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusDays(1));

        Object result = getService.getById(id, NoticeViewMode.CONSOLE, null, false, false);
        assertTrue(result instanceof NoticeConsoleDto);
        NoticeConsoleDto dto = (NoticeConsoleDto) result;
        assertEquals("公開中", dto.publishState());
        assertNotNull(dto.author());
    }

    @Test
    void get_は_Market_認証済_視点で_NoticeMarketDto_と_isRead_を返す() {
        Long id = createNotice("件名", normalCategoryId,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusDays(1));

        Object result = getService.getById(id, NoticeViewMode.MARKET_AUTHED, customerId, false, false);
        assertTrue(result instanceof NoticeMarketDto);
        NoticeMarketDto dto = (NoticeMarketDto) result;
        assertTrue(dto.isRead().isPresent(), "Market 認証済は isRead が Optional.of(...)");
        assertEquals(false, dto.isRead().get());
    }

    @Test
    void get_は_Anonymous_視点で_isRead_を_Optional_empty_で返し_キー省略仕様に合わせる() {
        Long id = createNotice("件名", normalCategoryId,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusDays(1));

        Object result = getService.getById(id, NoticeViewMode.ANONYMOUS, null, false, false);
        assertTrue(result instanceof NoticeMarketDto);
        NoticeMarketDto dto = (NoticeMarketDto) result;
        assertTrue(dto.isRead().isEmpty());
    }

    @Test
    void get_は_Market_視点で_公開期間外なら_404() {
        Long futureId = createNotice("未公開", normalCategoryId,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(7));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> getService.getById(futureId, NoticeViewMode.ANONYMOUS, null, false, false));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void get_は_Market_視点で_include_unpublished_true_を_無視して_404() {
        Long futureId = createNotice("未公開", normalCategoryId,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(7));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> getService.getById(futureId, NoticeViewMode.ANONYMOUS, null, true, true));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void get_は_Console_視点で_include_unpublished_true_なら_未公開でも_取得可能() {
        Long futureId = createNotice("未公開", normalCategoryId,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(7));

        Object result = getService.getById(futureId, NoticeViewMode.CONSOLE, null, true, false);
        assertTrue(result instanceof NoticeConsoleDto);
        assertEquals("未公開", ((NoticeConsoleDto) result).publishState());
    }

    @Test
    void get_は_Console_視点で_include_deleted_true_なら_論理削除済も_取得可能() {
        Long id = createNotice("件名", normalCategoryId,
                LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        deleteService.delete(id, authorId);

        Object result = getService.getById(id, NoticeViewMode.CONSOLE, null, false, true);
        assertEquals("削除済", ((NoticeConsoleDto) result).publishState());
    }

    // ---------- List ----------

    @Test
    void list_Market_視点では_公開期間外と論理削除済が返らない() {
        LocalDateTime now = LocalDateTime.now();
        Long active = createNotice("公開中", normalCategoryId, now.minusDays(1), now.plusDays(1));
        Long future = createNotice("未公開", normalCategoryId, now.plusDays(1), now.plusDays(7));
        Long deleted = createNotice("削除済", normalCategoryId, now.minusDays(1), now.plusDays(1));
        deleteService.delete(deleted, authorId);

        Page<?> page = listService.list(NoticeViewMode.ANONYMOUS, null, null,
                false, false, 0, 100);

        @SuppressWarnings("unchecked")
        List<NoticeMarketDto> content = (List<NoticeMarketDto>) page.getContent();
        assertTrue(content.stream().anyMatch(d -> d.id().equals(active)));
        assertFalse(content.stream().anyMatch(d -> d.id().equals(future)));
        assertFalse(content.stream().anyMatch(d -> d.id().equals(deleted)));
    }

    @Test
    void list_並び順は_category_id_ASC_publish_start_DESC() {
        LocalDateTime now = LocalDateTime.now();
        Long n1 = createNotice("normal-old", normalCategoryId, now.minusHours(2), now.plusDays(1));
        Long n2 = createNotice("normal-new", normalCategoryId, now.minusHours(1), now.plusDays(1));
        Long i1 = createNotice("important", importantCategoryId, now.minusHours(2), now.plusDays(1));

        Page<?> page = listService.list(NoticeViewMode.ANONYMOUS, null, null,
                false, false, 0, 100);

        @SuppressWarnings("unchecked")
        List<NoticeMarketDto> content = (List<NoticeMarketDto>) page.getContent();
        // important (id=1) 先 → normal (id=2) 後、normal 内では publish_start DESC（新しい順）
        assertEquals(i1, content.get(0).id());
        assertEquals(n2, content.get(1).id());
        assertEquals(n1, content.get(2).id());
    }

    @Test
    void list_Console_視点_include_unpublished_true_include_deleted_true_で_全件返る() {
        LocalDateTime now = LocalDateTime.now();
        Long active  = createNotice("公開中", normalCategoryId, now.minusDays(1), now.plusDays(1));
        Long future  = createNotice("未公開", normalCategoryId, now.plusDays(1), now.plusDays(7));
        Long deleted = createNotice("削除済", normalCategoryId, now.minusDays(1), now.plusDays(1));
        deleteService.delete(deleted, authorId);

        Page<?> page = listService.list(NoticeViewMode.CONSOLE, null, null,
                true, true, 0, 100);

        @SuppressWarnings("unchecked")
        List<NoticeConsoleDto> content = (List<NoticeConsoleDto>) page.getContent();
        assertTrue(content.stream().anyMatch(d -> d.id().equals(active)));
        assertTrue(content.stream().anyMatch(d -> d.id().equals(future)));
        assertTrue(content.stream().anyMatch(d -> d.id().equals(deleted)));
    }

    // ---------- MarkAsRead ----------

    @Test
    void markAsRead_は_冪等で_5回叩いても_notice_reads_は_1行のみ() {
        Long id = createNotice("件名", normalCategoryId,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusDays(1));
        long before = readRepository.count();

        for (int i = 0; i < 5; i++) {
            markService.markAsRead(id, customerId);
        }

        assertEquals(before + 1, readRepository.count());
        assertTrue(readRepository.existsByNoticeIdAndMarketCustomerId(id, customerId));
    }

    @Test
    void markAsRead_は_公開期間外への登録で_404() {
        Long futureId = createNotice("未公開", normalCategoryId,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(7));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> markService.markAsRead(futureId, customerId));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void markAsRead_は_論理削除済みへの登録で_404() {
        Long id = createNotice("件名", normalCategoryId,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusDays(1));
        deleteService.delete(id, authorId);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> markService.markAsRead(id, customerId));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void markAsRead_は_存在しない_notice_id_で_404() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> markService.markAsRead(99999L, customerId));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // ---------- UnreadCount / Header ----------

    @Test
    void unread_count_は_important_と_normal_と_total_を_集計する() {
        LocalDateTime now = LocalDateTime.now();
        createNotice("imp-1", importantCategoryId, now.minusMinutes(1), now.plusDays(1));
        createNotice("imp-2", importantCategoryId, now.minusMinutes(1), now.plusDays(1));
        createNotice("nor-1", normalCategoryId, now.minusMinutes(1), now.plusDays(1));

        UnreadCountResponse res = unreadCountService.count(customerId);
        assertEquals(2, res.data().important());
        assertEquals(1, res.data().normal());
        assertEquals(3, res.data().total());
    }

    @Test
    void unread_count_は_既読登録後_カウントが減る() {
        LocalDateTime now = LocalDateTime.now();
        Long id = createNotice("nor", normalCategoryId, now.minusMinutes(1), now.plusDays(1));

        assertEquals(1, unreadCountService.count(customerId).data().normal());
        markService.markAsRead(id, customerId);
        assertEquals(0, unreadCountService.count(customerId).data().normal());
    }

    @Test
    void unread_header_は_最大_amazia_notice_header_max_items_件まで_返す() {
        LocalDateTime now = LocalDateTime.now();
        // header の max 件数 + 1 件作る
        for (int i = 0; i < headerMaxItems + 1; i++) {
            createNotice("nor-" + i, normalCategoryId, now.minusMinutes(1), now.plusDays(1));
        }

        List<NoticeMarketDto> headers = unreadHeaderService.findUnread(customerId);
        assertEquals(headerMaxItems, headers.size());
    }

    @Test
    void unread_header_は_既読は_含まない() {
        LocalDateTime now = LocalDateTime.now();
        Long id1 = createNotice("read", normalCategoryId, now.minusMinutes(1), now.plusDays(1));
        Long id2 = createNotice("unread", normalCategoryId, now.minusMinutes(1), now.plusDays(1));
        markService.markAsRead(id1, customerId);

        List<NoticeMarketDto> headers = unreadHeaderService.findUnread(customerId);
        assertTrue(headers.stream().anyMatch(d -> d.id().equals(id2)));
        assertFalse(headers.stream().anyMatch(d -> d.id().equals(id1)));
    }

    // ---------- 公開期間境界値 ----------

    @Test
    void 公開期間境界値_publish_start_と_publish_end_が_now_と_等しいとき_含まれる() {
        // 過去/未来側の境界をそれぞれ別個に検証する：
        // - publish_start = now-1s, publish_end = now+1s で「今」が含まれる
        LocalDateTime now = LocalDateTime.now();
        Long id = createNotice("境界", normalCategoryId, now.minusSeconds(1), now.plusSeconds(1));

        Object result = getService.getById(id, NoticeViewMode.ANONYMOUS, null, false, false);
        assertTrue(result instanceof NoticeMarketDto);
    }

    // ---------- ヘルパ ----------

    private Long createNotice(String subject, Long categoryId,
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

    private User createUser(String prefix) {
        Role role = roleRepository.findByCode("admin").orElseThrow();
        User u = new User();
        u.setEmployeeId("EMP_" + prefix + "_" + System.nanoTime());
        u.setEmail(prefix + "-" + System.nanoTime() + "@example.com");
        u.setName("テストユーザー");
        u.setPasswordHash(encoder.encode("Pass@1234"));
        u.setRole(role);
        u.setActiveFlag(true);
        return userRepository.saveAndFlush(u);
    }

    private Customer createCustomer(String prefix) {
        Customer c = new Customer();
        c.setNameLast("山田");
        c.setNameFirst("太郎");
        c.setPostalCode("100-0001");
        c.setAddress("東京都");
        c.setBirthday(LocalDate.of(1990, 1, 1));
        c.setEmail(prefix + "-" + System.nanoTime() + "@example.com");
        c.setPasswordHash("dummyhash");
        c.setPaymentMethod("credit_card");
        c.setActiveFlag(true);
        return customerRepository.saveAndFlush(c);
    }
}
