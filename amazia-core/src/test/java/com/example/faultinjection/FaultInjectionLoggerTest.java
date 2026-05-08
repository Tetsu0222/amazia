package com.example.faultinjection;

import com.example.faultinjection.entity.FaultInjectionLog;
import com.example.faultinjection.repository.FaultInjectionLogRepository;
import com.example.faultinjection.service.FaultInjectionLogger;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 5-1: FaultInjectionLogger の動作検証。
 *
 * <ul>
 *   <li>FIL_1：{@code dev} プロファイルで {@code environment='dev'} を保存できる</li>
 *   <li>FIL_2：{@code production} プロファイルが含まれていれば例外で停止</li>
 *   <li>FIL_3：DB CHECK で {@code production} 値の保存は拒否される</li>
 * </ul>
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
class FaultInjectionLoggerTest {

    @Autowired private FaultInjectionLogger logger;
    @Autowired private FaultInjectionLogRepository repository;

    @Test
    void FIL_1_test_プロファイルで保存し検索できる() {
        FaultInjectionLog saved = logger.log("FilTest_Saver", "scheduler", "summary-1");

        assertNotNull(saved.getId());
        assertEquals("FilTest_Saver", saved.getInjectorName());
        assertEquals("dev", saved.getEnvironment(),
                "test プロファイルでは dev / staging のいずれも該当しないが既定で dev に解決される");

        List<FaultInjectionLog> hits = repository.findByInjectorNameOrderByCreatedAtDesc("FilTest_Saver");
        assertEquals(1, hits.size());
    }

    @Test
    void FIL_2_resolveEnvironment_は_production_プロファイルで例外を投げる() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("production");
        FaultInjectionLogger isolated = new FaultInjectionLogger(repository, env);

        assertThrows(IllegalStateException.class, isolated::resolveEnvironment);
    }

    @Test
    void FIL_3_resolveEnvironment_は_staging_を優先する() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("staging");
        FaultInjectionLogger isolated = new FaultInjectionLogger(repository, env);
        assertEquals("staging", isolated.resolveEnvironment());
    }

    @Test
    void FIL_4_DB_CHECK_で_production_値の_INSERT_は拒否される() {
        FaultInjectionLog illegal = new FaultInjectionLog();
        illegal.setInjectorName("FilTest_DirectProd");
        illegal.setTriggeredAt(java.time.LocalDateTime.now());
        illegal.setTriggeredBy("scheduler");
        illegal.setEnvironment("production");

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(illegal));
    }

    @Test
    void FIL_5_resolveEnvironment_の存在を_Environment_で外部から確認できる() {
        // Spring が DI する Environment が利用可能な test プロファイルであることを
        // sanity check として明示
        FaultInjectionLogger.class.getDeclaredFields(); // compile-time guard
        Environment env = new MockEnvironment();
        ((MockEnvironment) env).setActiveProfiles("dev");
        FaultInjectionLogger isolated = new FaultInjectionLogger(repository, env);
        assertEquals("dev", isolated.resolveEnvironment());
    }
}
