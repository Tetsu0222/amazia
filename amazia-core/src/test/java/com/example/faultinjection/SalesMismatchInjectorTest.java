package com.example.faultinjection;

import com.example.batch.service.RandomGeneratorAdapter;
import com.example.faultinjection.entity.FaultInjectionLog;
import com.example.faultinjection.repository.FaultInjectionLogRepository;
import com.example.faultinjection.service.FaultInjectionLogger;
import com.example.faultinjection.service.SalesMismatchInjector;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * フェーズ17 Step 5-2: SalesMismatchInjector の動作検証（H-7 / N-6）。
 *
 * <ul>
 *   <li>SMI_1：{@code enabled=false} なら絶対に発火しない</li>
 *   <li>SMI_2：{@code enabled=true} で確率に当選すると {@code fault_injection_logs} に
 *       履歴が残り {@code true} を返す</li>
 *   <li>SMI_3：{@code injectOnce} は確率を無視して必ず履歴を残す</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "amazia.simulation.fault-injection.enabled=true",
        "amazia.simulation.fault-injection.sales-mismatch-rate=0.5"
})
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
class SalesMismatchInjectorTest {

    @Autowired private SalesMismatchInjector injector;
    @Autowired private FaultInjectionLogRepository repository;
    @Autowired private FaultInjectionLogger faultLogger;

    @MockBean private RandomGeneratorAdapter random;

    @BeforeEach
    void cleanupPriorLogs() {
        // 同じ ApplicationContext を共有する H2 in-memory DB のため、
        // 他テストで残った同名 injector のレコードを毎テスト前にクリアする
        repository.deleteAll(repository
                .findByInjectorNameOrderByCreatedAtDesc(SalesMismatchInjector.INJECTOR_NAME));
    }

    @Test
    void SMI_1_enabled_false_なら発火しない() {
        when(random.nextDouble()).thenReturn(0.01); // どんな低い値でも
        ReflectionTestUtils.setField(injector, "enabled", false);

        assertFalse(injector.shouldInject("scheduler"));

        List<FaultInjectionLog> logs = repository
                .findByInjectorNameOrderByCreatedAtDesc(SalesMismatchInjector.INJECTOR_NAME);
        assertEquals(0, logs.size());
    }

    @Test
    void SMI_2_enabled_true_かつ確率当選で履歴が残る() {
        when(random.nextDouble()).thenReturn(0.1); // 0.5 未満なので当選
        ReflectionTestUtils.setField(injector, "enabled", true);

        assertTrue(injector.shouldInject("scheduler"));

        List<FaultInjectionLog> logs = repository
                .findByInjectorNameOrderByCreatedAtDesc(SalesMismatchInjector.INJECTOR_NAME);
        assertEquals(1, logs.size());
        assertEquals("scheduler", logs.get(0).getTriggeredBy());
    }

    @Test
    void SMI_3_確率閾値以上では発火しない() {
        when(random.nextDouble()).thenReturn(0.99);
        ReflectionTestUtils.setField(injector, "enabled", true);

        assertFalse(injector.shouldInject("scheduler"));
    }

    @Test
    void SMI_4_injectOnce_は確率を無視して履歴を残す() {
        ReflectionTestUtils.setField(injector, "enabled", false);

        injector.injectOnce("manual:user_id=1");

        List<FaultInjectionLog> logs = repository
                .findByInjectorNameOrderByCreatedAtDesc(SalesMismatchInjector.INJECTOR_NAME);
        assertEquals(1, logs.size());
        assertEquals("manual:user_id=1", logs.get(0).getTriggeredBy());
        // 残った Bean を確かめるためのサニティ
        assertNotNull(faultLogger);
    }
}
