package com.example.batch;

import com.example.batch.config.BatchResult;
import com.example.batch.job.OperationLogArchiveJob;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.entity.OperationLogArchive;
import com.example.operationlog.repository.OperationLogArchiveRepository;
import com.example.operationlog.repository.OperationLogRepository;
import com.example.shared.config.TestAwsConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 4-4: OperationLogArchiveJob の TDD（設計書 §3.3 ② / 計画書 §5-4）。
 *
 * <p>{@code archiveBefore(threshold)} を直接呼び、INSERT → DELETE が同 id で
 * 実施されることを確認する。
 */
@SpringBootTest(properties = "amazia.batch.scheduler-enabled=true")
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
class OperationLogArchiveJobTest {

    @Autowired private OperationLogArchiveJob job;
    @Autowired private OperationLogRepository operationLogRepository;
    @Autowired private OperationLogArchiveRepository archiveRepository;
    @Autowired private TransactionTemplate tx;

    @PersistenceContext private EntityManager em;

    @Test
    void OLA_1_閾値以前のログがアーカイブテーブルに移送され元テーブルから消える() {
        Long oldId = persistLogWithCreatedAt(LocalDateTime.now().minusYears(2));
        Long newId = persistLogWithCreatedAt(LocalDateTime.now().minusDays(1));

        BatchResult result = job.archiveBefore(LocalDateTime.now().minusYears(1));

        assertTrue(result.targetCount() >= 1);
        assertTrue(operationLogRepository.findById(oldId).isEmpty(),
                "閾値以前のログは元テーブルから削除");
        assertTrue(operationLogRepository.findById(newId).isPresent(),
                "閾値以降のログは残る");

        Optional<OperationLogArchive> archived = archiveRepository.findById(oldId);
        assertTrue(archived.isPresent(), "アーカイブ先に同 id で INSERT 済み");
        assertNotNull(archived.get().getArchivedAt());
        assertEquals("test_action", archived.get().getAction());
    }

    @Test
    void OLA_2_閾値以前のログが0件なら何もしない() {
        // 既存の古いログを念のため除外するため、未来の閾値でテストする
        BatchResult result = job.archiveBefore(LocalDateTime.now().minusYears(1000));

        assertEquals(0, result.targetCount());
        assertEquals(0, result.successCount());
    }

    Long persistLogWithCreatedAt(LocalDateTime createdAt) {
        return tx.execute(status -> {
            OperationLog log = new OperationLog();
            log.setUserId(1L);
            log.setAction("test_action");
            log.setComment("OLA テスト");
            OperationLog saved = operationLogRepository.saveAndFlush(log);
            // @PrePersist で created_at が NOW に固定されるため、ネイティブ UPDATE で指定値に上書き
            em.createNativeQuery("UPDATE operation_logs SET created_at = :ts WHERE id = :id")
                    .setParameter("ts", createdAt)
                    .setParameter("id", saved.getId())
                    .executeUpdate();
            em.clear();
            return saved.getId();
        });
    }
}
