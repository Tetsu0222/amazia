package com.example.inquiry;

import com.example.inquiry.dto.InquiryDetailResponse;
import com.example.inquiry.dto.InquiryListFilter;
import com.example.inquiry.dto.InquiryListResponse;
import com.example.inquiry.dto.InquiryStatusMutationContext;
import com.example.inquiry.dto.MarketCreateInquiryRequest;
import com.example.inquiry.dto.ReplyInquiryCommand;
import com.example.inquiry.entity.Inquiry;
import com.example.inquiry.entity.InquiryMessage;
import com.example.inquiry.exception.ForbiddenInquiryAccessException;
import com.example.inquiry.exception.IllegalInquiryStatusTransitionException;
import com.example.inquiry.exception.InquiryNotFoundException;
import com.example.inquiry.exception.InquiryValidationException;
import com.example.inquiry.repository.InquiryMessageRepository;
import com.example.inquiry.repository.InquiryRepository;
import com.example.inquiry.service.CreateInquiryService;
import com.example.inquiry.service.GetInquiryService;
import com.example.inquiry.service.GetUnreadInquiryCountService;
import com.example.inquiry.service.ListInquiryService;
import com.example.inquiry.service.ReplyInquiryService;
import com.example.inquiry.service.UpdateInquiryStatusService;
import com.example.market.customer.entity.Customer;
import com.example.market.customer.repository.CustomerRepository;
import com.example.notification.entity.ConsoleNotification;
import com.example.notification.repository.ConsoleNotificationRepository;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ18 Step 3: Service / Controller の主要ユースケース検証。
 *
 * <p>正常系・異常系・通知発火（payload_hash の prefix 確認）・status 遷移ルール・
 * Market 顧客の IDOR 拒否・内部メモ拒否などをカバーする（設計書 §11.1）。
 *
 * <p>BatchAlertNotifier は phase17 の REQUIRES_NEW で console_notifications に書き込むため、
 * @Transactional のロールバックを貫通して残置する。テスト前後でクリーンアップする。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class InquiryServiceTest {

    @Autowired private CreateInquiryService createInquiryService;
    @Autowired private ListInquiryService listInquiryService;
    @Autowired private GetInquiryService getInquiryService;
    @Autowired private ReplyInquiryService replyInquiryService;
    @Autowired private UpdateInquiryStatusService updateInquiryStatusService;
    @Autowired private GetUnreadInquiryCountService getUnreadInquiryCountService;

    @Autowired private InquiryRepository inquiryRepository;
    @Autowired private InquiryMessageRepository messageRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ConsoleNotificationRepository consoleNotificationRepository;
    @Autowired private OperationLogRepository operationLogRepository;

    private Long customerId;
    private Long otherCustomerId;

    @BeforeEach
    void setUp() {
        // phase17 BatchAlertNotifier は REQUIRES_NEW で書き込むため、本テストの dispatch 由来の
        // inquiry_alerts レコードを掃除しておかないと累積する（051 派生③と同様）。
        consoleNotificationRepository.deleteAll(consoleNotificationRepository
                .findByTargetSubscriptionTagAndReadByUserIdIsNullOrderByCreatedAtDesc("inquiry_alerts"));

        customerId = customerRepository.saveAndFlush(newCustomer("山田", "太郎",
                "yamada-svc@example.com")).getId();
        otherCustomerId = customerRepository.saveAndFlush(newCustomer("佐藤", "花子",
                "sato-svc@example.com")).getId();
    }

    // ============================================================
    // CRT: CreateInquiryService
    // ============================================================

    @Test
    void CRT1_create_すると_inquiries_と_初回_inquiry_messages_が同一トランザクションで_INSERT() {
        Long inquiryId = createInquiryService.create(
                new MarketCreateInquiryRequest("件名 A", "本文 A", null, null), customerId);

        assertNotNull(inquiryId);
        Inquiry inquiry = inquiryRepository.findById(inquiryId).orElseThrow();
        assertEquals("NEW", inquiry.getStatus());
        assertEquals(customerId, inquiry.getUserId());

        List<InquiryMessage> msgs = messageRepository.findByInquiryIdOrderByCreatedAtAsc(inquiryId);
        assertEquals(1, msgs.size());
        assertEquals("market_customer", msgs.get(0).getSenderType());
        assertEquals(customerId, msgs.get(0).getSenderId());
        assertFalse(msgs.get(0).getIsInternalNote());
    }

    @Test
    void CRT2_create_すると_console_notifications_に_inquiry_created_の_payload_hash_で_1件_INSERT() {
        Long inquiryId = createInquiryService.create(
                new MarketCreateInquiryRequest("件名", "本文", null, null), customerId);

        List<ConsoleNotification> notifs = consoleNotificationRepository
                .findByTargetSubscriptionTagAndReadByUserIdIsNullOrderByCreatedAtDesc("inquiry_alerts");
        assertEquals(1, notifs.size());
        ConsoleNotification cn = notifs.get(0);
        assertEquals("INFO", cn.getLevel());
        assertEquals("inquiry_alerts", cn.getTargetSubscriptionTag());
        assertNotNull(cn.getPayloadHash());
        assertEquals(64, cn.getPayloadHash().length()); // SHA-256 hex
        assertNotNull(cn.getTitle());
        assertNotNull(cn.getBody());
        // タイトルに inquiry_id と subject が展開されていること
        assertTrue(cn.getTitle().contains(String.valueOf(inquiryId)));
        assertTrue(cn.getTitle().contains("件名"));
    }

    @Test
    void CFG1_subject_max_length_を_超過すると_InquiryValidationException() {
        // application-test.properties で subject-max-length=100。101 文字を投入。
        String over = "あ".repeat(101);
        assertThrows(InquiryValidationException.class, () ->
                createInquiryService.create(new MarketCreateInquiryRequest(over, "本文", null, null),
                        customerId));
    }

    @Test
    void ERR4_target_type_delivery_かつ_target_id_NULL_は_InquiryValidationException() {
        assertThrows(InquiryValidationException.class, () ->
                createInquiryService.create(
                        new MarketCreateInquiryRequest("件名", "本文", "delivery", null), customerId));
    }

    @Test
    void ERR8_target_type_未知値_は_InquiryValidationException() {
        assertThrows(InquiryValidationException.class, () ->
                createInquiryService.create(
                        new MarketCreateInquiryRequest("件名", "本文", "unknown_thing", 1L), customerId));
    }

    // ============================================================
    // LIST: ListInquiryService
    // ============================================================

    @Test
    void LIST2_Market_一覧_は他顧客の問い合わせを含まない() {
        createInquiryService.create(new MarketCreateInquiryRequest("自分A", "本文", null, null), customerId);
        createInquiryService.create(new MarketCreateInquiryRequest("自分B", "本文", null, null), customerId);
        createInquiryService.create(new MarketCreateInquiryRequest("他人", "本文", null, null), otherCustomerId);

        Page<InquiryListResponse> page = listInquiryService.listForMarket(customerId, PageRequest.of(0, 20));
        assertEquals(2, page.getTotalElements());
        page.getContent().forEach(r -> assertEquals(customerId, r.userId()));
    }

    @Test
    void LIST1_Console_一覧_status_filter_動作() {
        Long aId = createInquiryService.create(
                new MarketCreateInquiryRequest("未対応", "本文", null, null), customerId);
        Long bId = createInquiryService.create(
                new MarketCreateInquiryRequest("完了", "本文", null, null), customerId);
        // status 変更（NEW → IN_PROGRESS → DONE）
        updateInquiryStatusService.update(new InquiryStatusMutationContext(bId, "IN_PROGRESS", null, 1L));
        updateInquiryStatusService.update(new InquiryStatusMutationContext(bId, "DONE", null, 1L));

        Page<InquiryListResponse> page = listInquiryService.listForConsole(
                new InquiryListFilter("DONE", null, null, null, null), PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        assertEquals(bId, page.getContent().get(0).id());
    }

    // ============================================================
    // GET: GetInquiryService
    // ============================================================

    @Test
    void GET1_Console_詳細_は内部メモを含めて返す() {
        Long inquiryId = createInquiryService.create(
                new MarketCreateInquiryRequest("件名", "本文", null, null), customerId);
        // 管理者の通常返信
        replyInquiryService.reply(new ReplyInquiryCommand(
                inquiryId, "admin_user", 1L, "管理者返信", false));
        // 内部メモ
        replyInquiryService.reply(new ReplyInquiryCommand(
                inquiryId, "admin_user", 1L, "内部メモ", true));

        InquiryDetailResponse resp = getInquiryService.getForConsole(inquiryId);
        assertEquals(3, resp.messages().size()); // 初回 + 通常返信 + 内部メモ
        assertTrue(resp.messages().stream().anyMatch(m -> m.isInternalNote()));
    }

    @Test
    void GET2_Market_詳細_は内部メモを除外() {
        Long inquiryId = createInquiryService.create(
                new MarketCreateInquiryRequest("件名", "本文", null, null), customerId);
        replyInquiryService.reply(new ReplyInquiryCommand(
                inquiryId, "admin_user", 1L, "管理者返信", false));
        replyInquiryService.reply(new ReplyInquiryCommand(
                inquiryId, "admin_user", 1L, "内部メモ", true));

        InquiryDetailResponse resp = getInquiryService.getForMarket(inquiryId, customerId);
        assertEquals(2, resp.messages().size()); // 初回 + 通常返信のみ
        assertTrue(resp.messages().stream().noneMatch(m -> m.isInternalNote()));
    }

    @Test
    void ERR3_他人の_inquiry_を_Market_API_で取得は_ForbiddenInquiryAccessException() {
        Long inquiryId = createInquiryService.create(
                new MarketCreateInquiryRequest("件名", "本文", null, null), customerId);

        assertThrows(ForbiddenInquiryAccessException.class, () ->
                getInquiryService.getForMarket(inquiryId, otherCustomerId));
    }

    @Test
    void ERR2_存在しない_inquiry_は_InquiryNotFoundException() {
        assertThrows(InquiryNotFoundException.class, () ->
                getInquiryService.getForConsole(999_999L));
    }

    // ============================================================
    // REP: ReplyInquiryService
    // ============================================================

    @Test
    void REP1_Market_顧客返信時_は_inquiry_replied_の_payload_hash_で通知() {
        Long inquiryId = createInquiryService.create(
                new MarketCreateInquiryRequest("件名", "本文", null, null), customerId);

        // create 由来の通知 1 件はすでに存在
        long beforeCount = consoleNotificationRepository
                .findByTargetSubscriptionTagAndReadByUserIdIsNullOrderByCreatedAtDesc("inquiry_alerts")
                .size();

        replyInquiryService.reply(new ReplyInquiryCommand(
                inquiryId, "market_customer", customerId, "顧客返信", false));

        long afterCount = consoleNotificationRepository
                .findByTargetSubscriptionTagAndReadByUserIdIsNullOrderByCreatedAtDesc("inquiry_alerts")
                .size();
        assertEquals(beforeCount + 1, afterCount);
    }

    @Test
    void REP2_管理者返信時_は通知を発火しない() {
        Long inquiryId = createInquiryService.create(
                new MarketCreateInquiryRequest("件名", "本文", null, null), customerId);
        long beforeCount = consoleNotificationRepository
                .findByTargetSubscriptionTagAndReadByUserIdIsNullOrderByCreatedAtDesc("inquiry_alerts")
                .size();

        replyInquiryService.reply(new ReplyInquiryCommand(
                inquiryId, "admin_user", 1L, "管理者返信", false));

        long afterCount = consoleNotificationRepository
                .findByTargetSubscriptionTagAndReadByUserIdIsNullOrderByCreatedAtDesc("inquiry_alerts")
                .size();
        assertEquals(beforeCount, afterCount);
    }

    @Test
    void ERR6_market_customer_が_isInternalNote_true_を渡すと_InquiryValidationException() {
        Long inquiryId = createInquiryService.create(
                new MarketCreateInquiryRequest("件名", "本文", null, null), customerId);
        // Service 直接呼出で内部メモを試みる
        assertThrows(InquiryValidationException.class, () ->
                replyInquiryService.reply(new ReplyInquiryCommand(
                        inquiryId, "market_customer", customerId, "内部メモのつもり", true)));
    }

    @Test
    void Market顧客_他人のinquiryへの返信は_ForbiddenInquiryAccessException() {
        Long inquiryId = createInquiryService.create(
                new MarketCreateInquiryRequest("件名", "本文", null, null), customerId);

        assertThrows(ForbiddenInquiryAccessException.class, () ->
                replyInquiryService.reply(new ReplyInquiryCommand(
                        inquiryId, "market_customer", otherCustomerId, "勝手返信", false)));
    }

    // ============================================================
    // UPD: UpdateInquiryStatusService
    // ============================================================

    @Test
    void UPD1_NEW_から_IN_PROGRESS_への遷移成功_と通知発火() {
        Long inquiryId = createInquiryService.create(
                new MarketCreateInquiryRequest("件名", "本文", null, null), customerId);
        long before = consoleNotificationRepository
                .findByTargetSubscriptionTagAndReadByUserIdIsNullOrderByCreatedAtDesc("inquiry_alerts")
                .size();

        updateInquiryStatusService.update(new InquiryStatusMutationContext(
                inquiryId, "IN_PROGRESS", null, 1L));

        Inquiry inquiry = inquiryRepository.findById(inquiryId).orElseThrow();
        assertEquals("IN_PROGRESS", inquiry.getStatus());
        long after = consoleNotificationRepository
                .findByTargetSubscriptionTagAndReadByUserIdIsNullOrderByCreatedAtDesc("inquiry_alerts")
                .size();
        assertEquals(before + 1, after);
    }

    @Test
    void UPD2_DONE_から_NEW_への巻き戻しが許容される() {
        Long inquiryId = createInquiryService.create(
                new MarketCreateInquiryRequest("件名", "本文", null, null), customerId);
        updateInquiryStatusService.update(new InquiryStatusMutationContext(inquiryId, "IN_PROGRESS", null, 1L));
        updateInquiryStatusService.update(new InquiryStatusMutationContext(inquiryId, "DONE", null, 1L));

        // DONE → NEW（双方向許容 / I-6）
        updateInquiryStatusService.update(new InquiryStatusMutationContext(inquiryId, "NEW", null, 1L));
        assertEquals("NEW", inquiryRepository.findById(inquiryId).orElseThrow().getStatus());
    }

    @Test
    void ERR1_同値遷移は_no_op_例外送出なし() {
        Long inquiryId = createInquiryService.create(
                new MarketCreateInquiryRequest("件名", "本文", null, null), customerId);

        // NEW → NEW は no-op
        assertDoesNotThrow(() ->
                updateInquiryStatusService.update(new InquiryStatusMutationContext(
                        inquiryId, "NEW", null, 1L)));
    }

    @Test
    void ERR1b_unknown_status_への遷移は_IllegalInquiryStatusTransitionException() {
        Long inquiryId = createInquiryService.create(
                new MarketCreateInquiryRequest("件名", "本文", null, null), customerId);

        assertThrows(IllegalInquiryStatusTransitionException.class, () ->
                updateInquiryStatusService.update(new InquiryStatusMutationContext(
                        inquiryId, "UNKNOWN_STATUS", null, 1L)));
    }

    // ============================================================
    // CNT: GetUnreadInquiryCountService
    // ============================================================

    @Test
    void CNT1_NEW_状態の_COUNT_を返す() {
        Long a = createInquiryService.create(new MarketCreateInquiryRequest("A", "本文", null, null), customerId);
        Long b = createInquiryService.create(new MarketCreateInquiryRequest("B", "本文", null, null), customerId);
        Long c = createInquiryService.create(new MarketCreateInquiryRequest("C", "本文", null, null), customerId);
        // b を IN_PROGRESS に
        updateInquiryStatusService.update(new InquiryStatusMutationContext(b, "IN_PROGRESS", null, 1L));
        // c を DONE に
        updateInquiryStatusService.update(new InquiryStatusMutationContext(c, "IN_PROGRESS", null, 1L));
        updateInquiryStatusService.update(new InquiryStatusMutationContext(c, "DONE", null, 1L));

        long count = getUnreadInquiryCountService.count();
        assertEquals(1, count);
    }

    // ============================================================
    // SUP: 抑制（60 分以内同一 payload_hash で suppressed=TRUE）
    // ============================================================

    @Test
    void SUP1_同一_inquiry_への_60分以内連続返信は_suppressed_TRUE() {
        Long inquiryId = createInquiryService.create(
                new MarketCreateInquiryRequest("件名", "本文", null, null), customerId);

        // 顧客が連続で 2 回返信（同一 payload_hash = SHA-256("inquiry_alerts:inquiry_replied:" + id)）
        replyInquiryService.reply(new ReplyInquiryCommand(
                inquiryId, "market_customer", customerId, "1回目", false));
        replyInquiryService.reply(new ReplyInquiryCommand(
                inquiryId, "market_customer", customerId, "2回目", false));

        // 同 payload_hash の 2 件目以降は suppressed=TRUE。
        // 親トランザクションの読み取り境界に縛られないよう findAll() で確認する。
        // タグでフィルタしたあと payloadHash 一致で集計する（タイトル文字列に依存しない）。
        String repliedPayloadHash = sha256("inquiry_alerts:inquiry_replied:" + inquiryId);

        List<ConsoleNotification> all = consoleNotificationRepository.findAll();
        long replied = all.stream()
                .filter(n -> repliedPayloadHash.equals(n.getPayloadHash()))
                .count();
        long suppressed = all.stream()
                .filter(n -> repliedPayloadHash.equals(n.getPayloadHash())
                        && Boolean.TRUE.equals(n.getSuppressed()))
                .count();
        assertEquals(2, replied,
                "2 回の返信通知が console_notifications に書き込まれているはず");
        assertEquals(1, suppressed,
                "2 件目以降は payload_hash 一致で suppressed=TRUE になるはず");
    }

    // ============================================================
    // OPLOG: operation_logs 記録（管理者操作のみ）
    // ============================================================

    @Test
    void OPLOG1_管理者の通常返信は_action_reply_inquiry_と_admin_reply_プレフィックスで記録() {
        Long inquiryId = createInquiryService.create(
                new MarketCreateInquiryRequest("件名", "本文", null, null), customerId);
        Long beforeCount = operationLogRepository.count();

        Long messageId = replyInquiryService.reply(new ReplyInquiryCommand(
                inquiryId, "admin_user", 1L, "管理者返信", false));

        List<OperationLog> logs = operationLogRepository.findByTargetTypeAndTargetId("inquiries", inquiryId);
        assertEquals(1, logs.size());
        OperationLog log = logs.get(0);
        assertEquals(1L, log.getUserId());
        assertEquals("reply_inquiry", log.getAction());
        assertEquals("ConsoleInquiryDetailPage", log.getScreenName());
        assertTrue(log.getApiName().contains("messages"));
        assertTrue(log.getComment().startsWith("[admin_reply]"));
        assertTrue(log.getComment().contains("message_id=" + messageId));
        assertEquals(beforeCount + 1, operationLogRepository.count());
    }

    @Test
    void OPLOG2_管理者の内部メモは_action_add_internal_note_と_internal_note_プレフィックス() {
        Long inquiryId = createInquiryService.create(
                new MarketCreateInquiryRequest("件名", "本文", null, null), customerId);

        Long messageId = replyInquiryService.reply(new ReplyInquiryCommand(
                inquiryId, "admin_user", 1L, "内部メモ", true));

        List<OperationLog> logs = operationLogRepository.findByTargetTypeAndTargetId("inquiries", inquiryId);
        assertEquals(1, logs.size());
        OperationLog log = logs.get(0);
        assertEquals("add_internal_note", log.getAction());
        assertTrue(log.getComment().startsWith("[internal_note]"));
        assertTrue(log.getComment().contains("message_id=" + messageId));
    }

    @Test
    void OPLOG3_顧客返信は_operation_logs_に記録されない() {
        Long inquiryId = createInquiryService.create(
                new MarketCreateInquiryRequest("件名", "本文", null, null), customerId);

        replyInquiryService.reply(new ReplyInquiryCommand(
                inquiryId, "market_customer", customerId, "顧客返信", false));

        List<OperationLog> logs = operationLogRepository.findByTargetTypeAndTargetId("inquiries", inquiryId);
        assertEquals(0, logs.size());
    }

    @Test
    void OPLOG4_ステータス変更は_action_update_inquiry_status_と_status_change_プレフィックス_reason付き() {
        Long inquiryId = createInquiryService.create(
                new MarketCreateInquiryRequest("件名", "本文", null, null), customerId);

        updateInquiryStatusService.update(new InquiryStatusMutationContext(
                inquiryId, "IN_PROGRESS", "確認中", 7L));

        List<OperationLog> logs = operationLogRepository.findByTargetTypeAndTargetId("inquiries", inquiryId);
        assertEquals(1, logs.size());
        OperationLog log = logs.get(0);
        assertEquals(7L, log.getUserId());
        assertEquals("update_inquiry_status", log.getAction());
        assertTrue(log.getComment().contains("[status_change]"));
        assertTrue(log.getComment().contains("旧:NEW"));
        assertTrue(log.getComment().contains("新:IN_PROGRESS"));
        assertTrue(log.getComment().contains("reason='確認中'"));
    }

    private static String sha256(String seed) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private Customer newCustomer(String last, String first, String email) {
        Customer c = new Customer();
        c.setNameLast(last);
        c.setNameFirst(first);
        c.setPostalCode("100-0001");
        c.setAddress("東京都");
        c.setBirthday(LocalDate.of(1990, 1, 1));
        c.setEmail(email);
        c.setPasswordHash("dummyhash");
        c.setPaymentMethod("credit_card");
        c.setActiveFlag(true);
        return c;
    }
}
