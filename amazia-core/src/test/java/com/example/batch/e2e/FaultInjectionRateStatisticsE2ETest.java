package com.example.batch.e2e;

import com.example.batch.service.RandomGeneratorAdapter;
import com.example.faultinjection.repository.FaultInjectionLogRepository;
import com.example.faultinjection.service.SalesMismatchInjector;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * phase17 Step 8 / E2E-2（設計書 §12.3 / 完了条件「フレーキー化を回避」）：
 * フォルトインジェクションの「{@code *_rate} で発火する」契約を統計的にではなく
 * 決定論的に検証する。{@link RandomGeneratorAdapter} をモック差し替え、
 * 「rate 未満を返す呼び出しは発火 / rate 以上を返す呼び出しは非発火」が
 * 一致することで {@code random.nextDouble() &gt;= rate} の判定が config 値で
 * 駆動されていることを担保する（R-14 候補）。
 *
 * <p>ステージング 1 日運転による Bernoulli 統計検証は本テストでは扱わず、
 * 運用ドキュメント側に記録する。
 */
@SpringBootTest(properties = {
        "amazia.simulation.fault-injection.enabled=true",
        "amazia.simulation.fault-injection.sales-mismatch-rate=0.05"
})
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Sql(
        scripts = "/cleanup/fault_injection_logs.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class FaultInjectionRateStatisticsE2ETest {

    @Autowired private SalesMismatchInjector injector;
    @Autowired private FaultInjectionLogRepository logRepository;
    @MockBean private RandomGeneratorAdapter random;

    @Test
    void E2E_2_random値が_rate_未満なら発火_rate_以上なら非発火() {
        // sales-mismatch-rate = 0.05。境界の手前と奥で挙動が決定論的に分かれる。
        when(random.nextDouble()).thenReturn(0.01);
        assertTrue(injector.shouldInject("e2e-2"),
                "0.01 < 0.05 なので MISMATCH 発火");

        when(random.nextDouble()).thenReturn(0.05);
        assertFalse(injector.shouldInject("e2e-2"),
                "0.05 >= 0.05 (>=) なので非発火");

        when(random.nextDouble()).thenReturn(0.10);
        assertFalse(injector.shouldInject("e2e-2"),
                "0.10 >= 0.05 なので非発火");

        // 発火 1 回ぶんだけ fault_injection_logs に SalesMismatchInjector の履歴が残ること。
        long logged = logRepository
                .findByInjectorNameOrderByCreatedAtDesc(SalesMismatchInjector.INJECTOR_NAME)
                .size();
        assertEquals(1L, logged,
                "確率発火に当選した 1 回ぶんだけログが残る（境界・閾値超は残らない）");
    }

    @Test
    void E2E_2_random値が_rate_以上なら発火しない_かつ履歴も残らない() {
        long beforeLogged = logRepository
                .findByInjectorNameOrderByCreatedAtDesc(SalesMismatchInjector.INJECTOR_NAME)
                .size();
        when(random.nextDouble()).thenReturn(0.99);
        assertFalse(injector.shouldInject("e2e-2"),
                "0.99 >= 0.05 で発火しない");
        long afterLogged = logRepository
                .findByInjectorNameOrderByCreatedAtDesc(SalesMismatchInjector.INJECTOR_NAME)
                .size();
        assertEquals(beforeLogged, afterLogged,
                "非発火時は fault_injection_logs に追記されない");
    }

}
