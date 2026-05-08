package com.example.batch;

import com.example.batch.entity.BatchExecution;
import com.example.batch.job.PostalCsvImportJob;
import com.example.batch.repository.BatchExecutionRepository;
import com.example.market.postal.service.ImportPostalCsvService;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * フェーズ17 Step 4-0: PostalCsvImportJob の TDD（設計書 §3.2 ① 取込本体）。
 *
 * <p>取込本体は外部 HTTP（KEN_ALL.CSV ダウンロード）を伴うため、
 * {@link ImportPostalCsvService} を MockBean で差し替え、ジョブ責務（呼び出し・結果記録）のみ検証する。
 *
 * <p>phaseX-9 Step 4: 自衛コード（@AfterEach cleanup → batchExecutionRepository.deleteAll）を
 * cleanup.sql + クラスレベル @Sql(BEFORE_TEST_METHOD) 方式へ置換。本クラスは @Transactional 不在のため、
 * 他テストの REQUIRES_NEW 経由貫通記録を確実にクリアできるよう @Sql で前置きクリーンアップする。
 */
@SpringBootTest(properties = "amazia.batch.scheduler-enabled=true")
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Sql(
        scripts = "/cleanup/batch_executions.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class PostalCsvImportJobTest {

    @Autowired private PostalCsvImportJob job;
    @Autowired private BatchExecutionRepository batchExecutionRepository;

    @MockBean private ImportPostalCsvService importService;

    @Test
    void POSTAL_IMPORT_1_正常系_取込件数がbatch_executionsに記録される() {
        when(importService.execute()).thenReturn(125_000);

        job.run("scheduler");

        verify(importService, times(1)).execute();
        List<BatchExecution> execs = batchExecutionRepository.findAll();
        assertEquals(1, execs.size());
        BatchExecution e = execs.get(0);
        assertEquals("PostalCsvImportJob", e.getJobName());
        assertEquals("SUCCESS", e.getStatus());
        assertEquals(125_000, e.getTargetCount());
        assertEquals(125_000, e.getSuccessCount());
        assertEquals(0, e.getFailureCount());
        assertEquals("scheduler", e.getTriggeredBy());
    }

    @Test
    void POSTAL_IMPORT_2_異常系_例外時はFAILEDで記録される() {
        when(importService.execute()).thenThrow(new RuntimeException("download failed"));

        job.run("scheduler");

        List<BatchExecution> execs = batchExecutionRepository.findAll();
        assertEquals(1, execs.size());
        BatchExecution e = execs.get(0);
        assertEquals("FAILED", e.getStatus());
        assertNotNull(e.getErrorSummary());
        assertTrue(e.getErrorSummary().contains("download failed"));
    }

    @Test
    void POSTAL_IMPORT_3_取込0件はSUCCESS判定_target0で記録() {
        // 0 件取込（CSV 空 or サーバ応答無し）でも例外を投げない実装を前提とした正常系。
        // failure=0 なので SUCCESS 扱い。後段の PostalAddressIntegrityCheckJob が件数閾値で WARN を出す責務。
        when(importService.execute()).thenReturn(0);

        job.run("scheduler");

        BatchExecution e = batchExecutionRepository.findAll().get(0);
        assertEquals("SUCCESS", e.getStatus());
        assertEquals(0, e.getTargetCount());
    }
}
