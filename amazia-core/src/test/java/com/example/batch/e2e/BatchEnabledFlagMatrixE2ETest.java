package com.example.batch.e2e;

import com.example.batch.config.OnDemandJob;
import com.example.batch.job.ApplyScheduledPricesJob;
import com.example.batch.job.DigestNotificationDispatchJob;
import com.example.batch.job.InventoryConsistencyCheckJob;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * phase17 Step 8 / E2E-3 〜 E2E-6（設計書 §12.3）：
 * バッチ ON/OFF 系フラグ 4 軸の組合せを ApplicationContext 単位で起動し、
 * Bean の存在 / 手動 API のレスポンスを観測する。
 *
 * <p>各シナリオは {@code @SpringBootTest} のネストクラスとして独立したコンテキストを起動するため、
 * フラグ切替に再起動相当の効果を持つ（K-2 / J-1 整合）。
 *
 * <ul>
 *   <li>E2E-3：{@code BATCH_ENABLED=false}（alias）→ scheduler / manual は OFF・digest は継続（J-1）</li>
 *   <li>E2E-4：scheduler=false + manual=true → 定期 Bean なし・手動は 200</li>
 *   <li>E2E-5：manual=false → 手動 503・フラグ戻すと再起動なしで 200（クラス分割で代替）</li>
 *   <li>E2E-6：scheduler=false + digest=true → DigestNotificationDispatchJob は継続</li>
 * </ul>
 */
class BatchEnabledFlagMatrixE2ETest {

    /**
     * E2E-3：alias {@code amazia.batch.scheduler-enabled=false} ＋
     * {@code amazia.batch.manual-trigger-enabled=false} の同時 OFF（{@code BATCH_ENABLED=false} 相当）。
     * Digest は独立フラグなので継続（既定 true）（J-1 整合）。
     */
    @SpringBootTest(properties = {
            "amazia.batch.scheduler-enabled=false",
            "amazia.batch.manual-trigger-enabled=false",
            // 本番では既定 true で「alias 落としても Digest は独立して継続」する。
            // テストプロファイルは application-test.properties で digest-enabled=false が
            // 既定のため、本シナリオの趣旨「Digest は alias 落とし時にも継続」を示すには明示的に
            // true 上書きが必要（J-1 の独立性を担保する config 駆動）。
            "amazia.batch.notifications.digest-enabled=true"
    })
    @Import(TestAwsConfig.class)
    @ActiveProfiles("test")
    static class E2E_3_BatchEnabledFalseAlias {

        @Autowired private ApplicationContext context;

        @Test
        void scheduler系業務ジョブ_Bean_は登録されない_かつ_Digest_は継続する() {
            assertThrows(NoSuchBeanDefinitionException.class,
                    () -> context.getBean(InventoryConsistencyCheckJob.JOB_NAME),
                    "scheduler-enabled=false なら InventoryConsistencyCheckJob Bean は非登録");
            assertThrows(NoSuchBeanDefinitionException.class,
                    () -> context.getBean(ApplyScheduledPricesJob.JOB_NAME),
                    "scheduler-enabled=false なら ApplyScheduledPricesJob Bean は非登録");
            // Digest は独立フラグ。alias OFF でも継続（既定 true）
            DigestNotificationDispatchJob digest = context.getBean(
                    DigestNotificationDispatchJob.JOB_NAME, DigestNotificationDispatchJob.class);
            assertNotNull(digest, "BATCH_DIGEST_ENABLED 既定 true で Bean が残る（J-1）");
        }
    }

    /**
     * E2E-4：scheduler=false / manual=true → 定期 Bean は登録されないが、
     * 手動 API が 200 を返す Bean だけは生きるシナリオ。
     * 本テストでは「定期 Bean の非登録」と「OnDemandJob マップへ載らないこと」を確認。
     */
    @SpringBootTest(properties = {
            "amazia.batch.scheduler-enabled=false",
            "amazia.batch.manual-trigger-enabled=true"
    })
    @Import(TestAwsConfig.class)
    @ActiveProfiles("test")
    static class E2E_4_SchedulerOffManualOn {

        @Autowired private ApplicationContext context;

        @Test
        void 定期業務_Bean_は不在だが_OnDemandJob_マップから_TriggerFaultInjectionJob_は引ける() {
            // ConditionalOnProperty(scheduler-enabled) で守られたジョブは Bean 化されない
            assertThrows(NoSuchBeanDefinitionException.class,
                    () -> context.getBean(InventoryConsistencyCheckJob.JOB_NAME));

            // 手動経路は OnDemandJob 実装の Bean を Map で解決する。TriggerFaultInjectionJob は
            // @Profile("!production") のみで scheduler-enabled に依存しないため残る。
            Map<String, OnDemandJob> beans = context.getBeansOfType(OnDemandJob.class);
            assertTrue(beans.containsKey("TriggerFaultInjectionJob"),
                    "@Profile('!production') のみのオンデマンドジョブは scheduler OFF でも生きる");
        }
    }

    /**
     * E2E-5：手動 OFF（{@code amazia.batch.manual-trigger-enabled=false}）で
     * {@code POST /api/console/batch/{job}/run} が 503 を返す。
     * 「フラグ戻すと再起動なしで 200」は本テストでは扱わず、別の Enabled クラスで代替検証する。
     */
    @SpringBootTest(properties = {
            "amazia.batch.manual-trigger-enabled=false"
    })
    @AutoConfigureMockMvc
    @Import({TestAwsConfig.class, RecordingJobBean.class})
    @ActiveProfiles("test")
    static class E2E_5_ManualOff {

        @Autowired private MockMvc mockMvc;

        @Test
        void 手動_API_は_503_を返す() throws Exception {
            mockMvc.perform(post("/api/console/batch/E2ERecordingJob/run")
                            .header("X-User-Id", "1"))
                    .andExpect(status().isServiceUnavailable());
        }
    }

    /**
     * E2E-5 補足：フラグ ON のコンテキストでは同 API が 200 を返すことで「フラグ戻すと復帰」を担保。
     * 同じ Bean が異なる ApplicationContext で起動するため「再起動なしで」の文言は厳密には
     * 単一プロセス内で実現できないが、Bean ライフサイクル上は「Bean 自体は生きていて
     * Controller の if ガードだけで 503 / 200 が切り替わる」ことを確認できる。
     * 既存の {@code BatchManualTriggerControllerTest.MANUAL_1} と重複を避けるため、
     * テスト専用ジョブで実ジョブの副作用を遮断する。
     */
    @SpringBootTest(properties = {
            "amazia.batch.manual-trigger-enabled=true"
    })
    @AutoConfigureMockMvc
    @Import({TestAwsConfig.class, RecordingJobBean.class})
    @ActiveProfiles("test")
    static class E2E_5_ManualOnRestoresImmediately {

        @Autowired private MockMvc mockMvc;
        @Autowired private E2ERecordingJob recordingJob;

        @Test
        void 手動_API_は_200_を返す_Bean_は同一でフラグ起因の_503_だけが消える() throws Exception {
            mockMvc.perform(post("/api/console/batch/E2ERecordingJob/run")
                            .header("X-User-Id", "7"))
                    .andExpect(status().isOk());
            assertEquals("manual:user_id=7", recordingJob.lastTriggeredBy.get(),
                    "manual-trigger-enabled=true でフラグ ON 復帰時に Bean が即時実行される");
        }
    }

    /** E2E-5 専用 OnDemandJob。フラグ切替で Bean ライフサイクルが影響しないことを確認するため、
     *  実ジョブを呼ばない純粋な記録ジョブを差し込む（{@code BatchManualTriggerControllerTest} と同パターン）。 */
    @TestConfiguration
    static class RecordingJobBean {
        @Bean(name = "E2ERecordingJob")
        E2ERecordingJob e2eRecordingJob() { return new E2ERecordingJob(); }
    }

    static class E2ERecordingJob implements OnDemandJob {
        final AtomicReference<String> lastTriggeredBy = new AtomicReference<>();
        @Override public String jobName() { return "E2ERecordingJob"; }
        @Override public void run(String triggeredBy) { lastTriggeredBy.set(triggeredBy); }
    }

    /**
     * E2E-6：scheduler=false ＋ digest=true → 業務バッチ Bean は不在だが
     * DigestNotificationDispatchJob は継続。E2E-3 と分離しているのは
     * 「digest を明示 true にしてもフラグの独立性が保たれること」を担保するため。
     */
    @SpringBootTest(properties = {
            "amazia.batch.scheduler-enabled=false",
            "amazia.batch.notifications.digest-enabled=true"
    })
    @Import(TestAwsConfig.class)
    @ActiveProfiles("test")
    static class E2E_6_SchedulerOffDigestOn {

        @Autowired private ApplicationContext context;

        @Test
        void 業務_Bean_は不在だが_Digest_Bean_は登録されている() {
            assertThrows(NoSuchBeanDefinitionException.class,
                    () -> context.getBean(InventoryConsistencyCheckJob.JOB_NAME));
            DigestNotificationDispatchJob digest = context.getBean(
                    DigestNotificationDispatchJob.JOB_NAME, DigestNotificationDispatchJob.class);
            assertNotNull(digest);
        }
    }
}
