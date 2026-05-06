package com.example.operationlog;

import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ14 Step A: OperationLog Entity の永続化検証。
 * Step A で追加した screen_name / api_name を含めて読み書きできることを確認。
 *
 * 命名規約: docs/ai_context/operation_logs_naming.md
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class OperationLogEntityTest {

    @Autowired
    private OperationLogRepository operationLogRepository;

    @Test
    void OperationLog_は_screen_name_と_api_name_を含めて保存と取得ができる() {
        OperationLog log = new OperationLog();
        log.setUserId(1L);
        log.setAction("update_shipping_status");
        log.setTargetType("deliveries");
        log.setTargetId(123L);
        log.setScreenName("console.delivery.update_status");
        log.setApiName("PATCH /api/deliveries/:id/status");
        log.setComment("[manual] PENDING -> SHIPPED");

        OperationLog saved = operationLogRepository.save(log);
        assertNotNull(saved.getId());

        OperationLog loaded = operationLogRepository.findById(saved.getId()).orElseThrow();
        assertEquals("update_shipping_status", loaded.getAction());
        assertEquals("console.delivery.update_status", loaded.getScreenName());
        assertEquals("PATCH /api/deliveries/:id/status", loaded.getApiName());
        assertEquals("[manual] PENDING -> SHIPPED", loaded.getComment());
    }
}
