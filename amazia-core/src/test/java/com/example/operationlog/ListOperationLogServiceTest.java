package com.example.operationlog;

import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import com.example.operationlog.dto.AdminOperationLogItem;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import com.example.operationlog.service.ListOperationLogService;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ14 Step B-6: 操作履歴一覧 Service の検証。
 *
 * 設計書 r4 phase14 §操作履歴管理。
 * users と JOIN した user_name 整形、screen_name / api_name / action での絞り込み、
 * createdAt DESC, id DESC の並び順を検証する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class ListOperationLogServiceTest {

    @Autowired private ListOperationLogService service;
    @Autowired private OperationLogRepository operationLogRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void operation_logsを_users_と_JOIN_して_user_name_を返す() {
        Long aliceId = createAdminUser("田中 太郎");
        seedLog(aliceId, "approve_sales_return", "sales_return", 1L,
                "console.sales_return.approve", "POST /api/sales-returns/:id/approve");

        List<AdminOperationLogItem> all = service.list(null, null, null);

        AdminOperationLogItem item = all.stream()
                .filter(l -> "approve_sales_return".equals(l.getAction()))
                .findFirst()
                .orElseThrow();
        assertEquals(aliceId, item.getUserId());
        assertEquals("田中 太郎", item.getUserName());
        assertEquals("sales_return", item.getTargetType());
        assertEquals(1L, item.getTargetId());
        assertEquals("console.sales_return.approve", item.getScreenName());
        assertEquals("POST /api/sales-returns/:id/approve", item.getApiName());
        assertNotNull(item.getCreatedAt());
    }

    @Test
    void screen_name_の部分一致で絞り込める() {
        Long me = createAdminUser("検索 太郎");
        seedLog(me, "approve_sales_return", "sales_return", 10L, "console.sales_return.approve", "POST /a");
        seedLog(me, "register_inbound",     "inbounds",     11L, "console.inbound.register",     "POST /b");

        List<AdminOperationLogItem> filtered = service.list("sales_return", null, null);
        assertTrue(filtered.stream().allMatch(l -> l.getScreenName().contains("sales_return")));
        assertTrue(filtered.stream().anyMatch(l -> l.getTargetId() == 10L));
        assertFalse(filtered.stream().anyMatch(l -> l.getTargetId() == 11L));
    }

    @Test
    void api_name_の部分一致で絞り込める() {
        Long me = createAdminUser("API 太郎");
        seedLog(me, "approve_sales_return", "sales_return", 20L, "screen-a", "POST /api/sales-returns/:id/approve");
        seedLog(me, "register_inbound",     "inbounds",     21L, "screen-b", "POST /api/inbounds");

        List<AdminOperationLogItem> filtered = service.list(null, "sales-returns", null);
        assertTrue(filtered.stream().allMatch(l -> l.getApiName().contains("sales-returns")));
        assertTrue(filtered.stream().anyMatch(l -> l.getTargetId() == 20L));
        assertFalse(filtered.stream().anyMatch(l -> l.getTargetId() == 21L));
    }

    @Test
    void action_の完全一致で絞り込める() {
        Long me = createAdminUser("Action 太郎");
        seedLog(me, "approve_sales_return", "sales_return", 30L, "screen-a", "api-a");
        seedLog(me, "reject_sales_return",  "sales_return", 31L, "screen-a", "api-b");

        List<AdminOperationLogItem> filtered = service.list(null, null, "approve_sales_return");
        assertTrue(filtered.stream().allMatch(l -> "approve_sales_return".equals(l.getAction())));
        assertTrue(filtered.stream().anyMatch(l -> l.getTargetId() == 30L));
        assertFalse(filtered.stream().anyMatch(l -> l.getTargetId() == 31L));
    }

    @Test
    void 空文字の検索条件は_NULL_と同じ扱い() {
        Long me = createAdminUser("空文字 太郎");
        seedLog(me, "approve_sales_return", "sales_return", 40L, "screen-a", "api-a");

        // 空文字を渡しても全件と同じ結果が返る
        List<AdminOperationLogItem> withEmpty = service.list("", "", "");
        List<AdminOperationLogItem> withNull  = service.list(null, null, null);
        assertEquals(withNull.size(), withEmpty.size());
    }

    @Test
    void createdAt_DESC_で並ぶ() {
        Long me = createAdminUser("並び順 太郎");
        Long firstId = seedLog(me, "approve_sales_return", "sales_return", 50L, "screen-a", "api-a");
        Long secondId = seedLog(me, "reject_sales_return", "sales_return", 51L, "screen-a", "api-a");

        List<AdminOperationLogItem> all = service.list(null, null, null);

        int firstIdx = -1, secondIdx = -1;
        for (int i = 0; i < all.size(); i++) {
            if (firstId.equals(all.get(i).getId())) firstIdx = i;
            if (secondId.equals(all.get(i).getId())) secondIdx = i;
        }
        assertTrue(secondIdx >= 0 && firstIdx >= 0);
        assertTrue(secondIdx < firstIdx, "新しい log ほどリスト先頭側に来る");
    }

    // ---- helpers -------------------------------------------------------

    private Long createAdminUser(String name) {
        User u = new User();
        u.setEmployeeId("E" + (System.nanoTime() % 100000));
        u.setEmail("ol-" + System.nanoTime() + "@example.com");
        u.setName(name);
        u.setPasswordHash("dummy");
        u.setActiveFlag(true);
        return userRepository.save(u).getId();
    }

    private Long seedLog(Long userId, String action, String targetType, Long targetId,
                         String screenName, String apiName) {
        OperationLog log = new OperationLog();
        log.setUserId(userId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setScreenName(screenName);
        log.setApiName(apiName);
        return operationLogRepository.save(log).getId();
    }
}
