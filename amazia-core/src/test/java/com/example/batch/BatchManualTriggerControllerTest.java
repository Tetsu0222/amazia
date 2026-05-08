package com.example.batch;

import com.example.batch.config.OnDemandJob;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * フェーズ17 Step 2: BatchManualTriggerController（MANUAL-1 / MANUAL-2）。
 *
 * <p>{@code amazia.batch.manual-trigger-enabled=true / false} は
 * クラス単位で {@code @SpringBootTest(properties = ...)} を切り替える。
 */
class BatchManualTriggerControllerTest {

    @SpringBootTest(properties = "amazia.batch.manual-trigger-enabled=true")
    @AutoConfigureMockMvc
    @Import({TestAwsConfig.class, ManualEnabledBeans.class})
    @ActiveProfiles("test")
    static class Enabled {

        @Autowired private MockMvc mockMvc;
        @Autowired private RecordingJob recordingJob;
        @Autowired private OperationLogRepository operationLogRepository;

        @Test
        void MANUAL_1_有効時_既存ジョブ名で200を返しジョブが起動される() throws Exception {
            mockMvc.perform(post("/api/console/batch/RecordingJob/run")
                            .header("X-User-Id", "42"))
                    .andExpect(status().isOk());
            assertEquals("manual:user_id=42", recordingJob.lastTriggeredBy.get());
        }

        @Test
        void MANUAL_2_未登録ジョブ名は404を返す() throws Exception {
            mockMvc.perform(post("/api/console/batch/NonExistentJob/run")
                            .header("X-User-Id", "1"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void X_User_Id未指定は400を返す() throws Exception {
            mockMvc.perform(post("/api/console/batch/RecordingJob/run"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void Step6_3_手動起動成功で_operation_logs_に_screen_name_と_api_name_が記録される() throws Exception {
            mockMvc.perform(post("/api/console/batch/RecordingJob/run")
                            .header("X-User-Id", "99"))
                    .andExpect(status().isOk());

            List<OperationLog> logs = operationLogRepository
                    .findByActionOrderByCreatedAtDesc("trigger_batch_manual");
            assertFalse(logs.isEmpty(), "operation_logs に手動起動ログが残ること");
            OperationLog latest = logs.get(0);
            assertEquals(99L, latest.getUserId());
            assertEquals("ConsoleBatchManagementPage", latest.getScreenName());
            assertEquals("POST /api/console/batch/RecordingJob/run", latest.getApiName());
            assertEquals("batch_jobs", latest.getTargetType());
        }

        @Test
        void Step6_3_未登録ジョブの場合は_operation_logs_に記録されない() throws Exception {
            long before = operationLogRepository
                    .findByActionOrderByCreatedAtDesc("trigger_batch_manual").size();

            mockMvc.perform(post("/api/console/batch/NonExistentJob/run")
                            .header("X-User-Id", "7"))
                    .andExpect(status().isNotFound());

            long after = operationLogRepository
                    .findByActionOrderByCreatedAtDesc("trigger_batch_manual").size();
            assertEquals(before, after, "404 のときは操作ログを残さない");
        }
    }

    @SpringBootTest(properties = "amazia.batch.manual-trigger-enabled=false")
    @AutoConfigureMockMvc
    @Import({TestAwsConfig.class, ManualEnabledBeans.class})
    @ActiveProfiles("test")
    static class Disabled {

        @Autowired private MockMvc mockMvc;

        @Test
        void MANUAL_1_無効時_既存ジョブ名でも503を返す() throws Exception {
            mockMvc.perform(post("/api/console/batch/RecordingJob/run")
                            .header("X-User-Id", "42"))
                    .andExpect(status().isServiceUnavailable());
        }
    }

    // ---- テスト専用 OnDemandJob ----

    @TestConfiguration
    static class ManualEnabledBeans {
        // Bean 名 = jobName。BatchManualTriggerController は Map<String,OnDemandJob> を Bean 名で解決する。
        @Bean(name = "RecordingJob")
        RecordingJob recordingJob() { return new RecordingJob(); }
    }

    /** AbstractBatchJob の一連の動作を絡めず、起動引数だけを記録する純粋な OnDemandJob。 */
    static class RecordingJob implements OnDemandJob {
        final AtomicReference<String> lastTriggeredBy = new AtomicReference<>();
        @Override public String jobName() { return "RecordingJob"; }
        @Override public void run(String triggeredBy) { lastTriggeredBy.set(triggeredBy); }
    }
}
