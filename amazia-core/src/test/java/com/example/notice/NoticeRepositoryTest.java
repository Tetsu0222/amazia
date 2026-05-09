package com.example.notice;

import com.example.auth.entity.Role;
import com.example.auth.entity.User;
import com.example.auth.repository.RoleRepository;
import com.example.auth.repository.UserRepository;
import com.example.market.customer.entity.Customer;
import com.example.market.customer.repository.CustomerRepository;
import com.example.notice.entity.Notice;
import com.example.notice.entity.NoticeCategory;
import com.example.notice.entity.NoticeRead;
import com.example.notice.repository.NoticeCategoryRepository;
import com.example.notice.repository.NoticeReadRepository;
import com.example.notice.repository.NoticeRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * フェーズ19 Step A1: Notice / NoticeCategory / NoticeRead Entity / Repository の永続化と
 * 制約（CHECK / UNIQUE / FK）検証。
 *
 * <p>config 駆動規約（4-1）に従い、分類マスタ ID は {@code amazia.notice.categories.*-id} を
 * @Value で取得する。ハードコード（{@code 1L} / {@code 2L}）は禁止。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class NoticeRepositoryTest {

    @Autowired private NoticeRepository noticeRepository;
    @Autowired private NoticeCategoryRepository categoryRepository;
    @Autowired private NoticeReadRepository readRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    @Value("${amazia.notice.categories.important-id}")
    private Long importantCategoryId;

    @Value("${amazia.notice.categories.normal-id}")
    private Long normalCategoryId;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void notice_categories_マスタは_important_と_normal_の_2件存在する() {
        List<NoticeCategory> all = categoryRepository.findAll();
        assertTrue(all.size() >= 2,
                "test-data.sql で投入された 2 件以上のマスタが存在すること");

        Optional<NoticeCategory> important = categoryRepository.findByCode("important");
        Optional<NoticeCategory> normal = categoryRepository.findByCode("normal");
        assertTrue(important.isPresent(), "important マスタが存在");
        assertTrue(normal.isPresent(), "normal マスタが存在");
        assertEquals(importantCategoryId, important.get().getId(),
                "config の important-id とマスタ ID が一致");
        assertEquals(normalCategoryId, normal.get().getId(),
                "config の normal-id とマスタ ID が一致");
    }

    @Test
    void notice_を保存すると_id_と_created_at_updated_at_が_自動設定される() {
        Long authorId = createUser().getId();
        Notice n = newNotice(authorId, "件名", LocalDateTime.now(), LocalDateTime.now().plusDays(7));

        Notice saved = noticeRepository.saveAndFlush(n);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    // CHECK (publish_start <= publish_end) と FK (category_id, author_id) は本番 MySQL 側
    // schema.sql で物理担保している。H2 テストは ddl-auto=create-drop で Entity から DDL を
    // 生成しており CHECK / 外部 FK が無いため、当該違反は再現できない（test_insights カテゴリ7-2）。
    // Service 層の二重防御（NoticePeriodValidator / category 存在チェック / actor 存在チェック）
    // は Step A2 の Service テストで担保する。

    @Test
    void notice_reads_の_同一_notice_と_customer_の重複登録は_UNIQUE_違反になる() {
        Long authorId = createUser().getId();
        Notice notice = noticeRepository.saveAndFlush(
                newNotice(authorId, "件名", LocalDateTime.now(), LocalDateTime.now().plusDays(1)));
        Long customerId = createCustomer("yamada-notice@example.com").getId();

        readRepository.saveAndFlush(newRead(notice.getId(), customerId));

        // 2 回目の同一ペアは UNIQUE 違反
        assertThrows(DataIntegrityViolationException.class,
                () -> readRepository.saveAndFlush(newRead(notice.getId(), customerId)));
    }

    @Test
    void notice_reads_の_existsByNoticeIdAndMarketCustomerId_が動作する() {
        Long authorId = createUser().getId();
        Notice notice = noticeRepository.saveAndFlush(
                newNotice(authorId, "件名", LocalDateTime.now(), LocalDateTime.now().plusDays(1)));
        Long customerId = createCustomer("sato-notice@example.com").getId();

        assertFalse(readRepository.existsByNoticeIdAndMarketCustomerId(notice.getId(), customerId));

        readRepository.saveAndFlush(newRead(notice.getId(), customerId));

        assertTrue(readRepository.existsByNoticeIdAndMarketCustomerId(notice.getId(), customerId));
    }

    @Test
    void findByIdAndDeletedAtIsNull_は論理削除済を除外する() {
        Long authorId = createUser().getId();
        Notice n = noticeRepository.saveAndFlush(
                newNotice(authorId, "件名", LocalDateTime.now(), LocalDateTime.now().plusDays(1)));

        assertTrue(noticeRepository.findByIdAndDeletedAtIsNull(n.getId()).isPresent());

        n.setDeletedAt(LocalDateTime.now());
        noticeRepository.saveAndFlush(n);

        assertTrue(noticeRepository.findByIdAndDeletedAtIsNull(n.getId()).isEmpty(),
                "論理削除後は findByIdAndDeletedAtIsNull で取得できない");
    }

    @Test
    void findByIdActiveAt_は公開期間外と論理削除済を除外する() {
        Long authorId = createUser().getId();
        LocalDateTime now = LocalDateTime.now();

        Notice active = noticeRepository.saveAndFlush(
                newNotice(authorId, "公開中", now.minusDays(1), now.plusDays(1)));
        Notice future = noticeRepository.saveAndFlush(
                newNotice(authorId, "未公開", now.plusDays(1), now.plusDays(7)));
        Notice past = noticeRepository.saveAndFlush(
                newNotice(authorId, "終了", now.minusDays(7), now.minusDays(1)));

        assertTrue(noticeRepository.findByIdActiveAt(active.getId(), now).isPresent());
        assertTrue(noticeRepository.findByIdActiveAt(future.getId(), now).isEmpty(),
                "未公開（publish_start > now）は対象外");
        assertTrue(noticeRepository.findByIdActiveAt(past.getId(), now).isEmpty(),
                "終了済（publish_end < now）は対象外");

        // 論理削除も除外
        active.setDeletedAt(now);
        noticeRepository.saveAndFlush(active);
        assertTrue(noticeRepository.findByIdActiveAt(active.getId(), now).isEmpty(),
                "論理削除済は対象外");
    }

    @Test
    void findByIdActiveAt_は公開期間境界値を含む() {
        Long authorId = createUser().getId();
        LocalDateTime now = LocalDateTime.now().withNano(0); // ミリ秒以下を切り捨て
        // publish_start = now ちょうど、publish_end = now ちょうど
        Notice boundary = noticeRepository.saveAndFlush(
                newNotice(authorId, "境界", now, now));

        assertTrue(noticeRepository.findByIdActiveAt(boundary.getId(), now).isPresent(),
                "publish_start == now == publish_end で含まれる（<= / >=）");
    }

    // --- ヘルパ ---

    private Notice newNotice(Long authorId, String subject,
                             LocalDateTime publishStart, LocalDateTime publishEnd) {
        Notice n = new Notice();
        n.setSubject(subject);
        n.setCategoryId(normalCategoryId);
        n.setBody("本文");
        n.setAuthorId(authorId);
        n.setPublishStart(publishStart);
        n.setPublishEnd(publishEnd);
        return n;
    }

    private NoticeRead newRead(Long noticeId, Long marketCustomerId) {
        NoticeRead r = new NoticeRead();
        r.setNoticeId(noticeId);
        r.setMarketCustomerId(marketCustomerId);
        return r;
    }

    private User createUser() {
        Role role = roleRepository.findByCode("admin").orElseThrow();
        User u = new User();
        u.setEmployeeId("EMP_NOTICE_" + System.nanoTime());
        u.setEmail("notice-author-" + System.nanoTime() + "@example.com");
        u.setName("お知らせ投稿者");
        u.setPasswordHash(encoder.encode("Pass@1234"));
        u.setRole(role);
        u.setActiveFlag(true);
        return userRepository.saveAndFlush(u);
    }

    private Customer createCustomer(String email) {
        Customer c = new Customer();
        c.setNameLast("山田");
        c.setNameFirst("太郎");
        c.setPostalCode("100-0001");
        c.setAddress("東京都");
        c.setBirthday(LocalDate.of(1990, 1, 1));
        c.setEmail(email);
        c.setPasswordHash("dummyhash");
        c.setPaymentMethod("credit_card");
        c.setActiveFlag(true);
        return customerRepository.saveAndFlush(c);
    }
}
