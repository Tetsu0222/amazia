package com.example.batch;

import com.example.batch.entity.BatchExecution;
import com.example.batch.repository.BatchExecutionRepository;
import com.example.batch.service.GetBatchExecutionService;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 6-0: バッチ実行履歴詳細 Service の TDD。
 * 設計書 §13.7.1 GET /api/console/batch/executions/{id}。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class GetBatchExecutionServiceTest {

    @Autowired private GetBatchExecutionService service;
    @Autowired private BatchExecutionRepository repository;

    @Test
    void 既存IDで詳細を返す_error_summaryを含む() {
        BatchExecution e = new BatchExecution();
        e.setJobName("InventoryConsistencyCheckJob");
        e.setStatus("FAILED");
        e.setStartedAt(LocalDateTime.now());
        e.setTriggeredBy("scheduler");
        e.setErrorSummary("RuntimeException: foo");
        BatchExecution saved = repository.saveAndFlush(e);

        BatchExecution got = service.get(saved.getId());

        assertEquals(saved.getId(), got.getId());
        assertEquals("RuntimeException: foo", got.getErrorSummary());
    }

    @Test
    void 未存在IDはResponseStatusException_NOT_FOUNDを投げる() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.get(99999L));
        assertEquals(404, ex.getStatusCode().value());
    }
}
