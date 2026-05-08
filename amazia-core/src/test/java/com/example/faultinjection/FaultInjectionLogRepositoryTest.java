package com.example.faultinjection;

import com.example.faultinjection.entity.FaultInjectionLog;
import com.example.faultinjection.repository.FaultInjectionLogRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 1: fault_injection_logs Entity / Repository の永続化検証。
 * 五重防御の DB CHECK 層 chk_fault_logs_no_prod が effective であることを確認する。
 *
 * <p>phaseX-9 Step 2 PoC: REQUIRES_NEW 経由貫通の自衛コード（@BeforeEach cleanupPriorLogs）を
 * cleanup.sql + クラスレベル @Sql 方式へ置換。test_insights.md カテゴリ 7-2 規約に準拠。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
@Sql(
        scripts = "/cleanup/fault_injection_logs.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class FaultInjectionLogRepositoryTest {

    @Autowired
    private FaultInjectionLogRepository repository;

    @Test
    void dev_および_staging_は_保存できる() {
        FaultInjectionLog devLog =
                newLog("SalesMismatchInjector", "dev");
        FaultInjectionLog stagingLog =
                newLog("InventoryMismatchInjector", "staging");

        FaultInjectionLog savedDev = repository.saveAndFlush(devLog);
        FaultInjectionLog savedStg = repository.saveAndFlush(stagingLog);

        assertNotNull(savedDev.getId());
        assertNotNull(savedStg.getId());
    }

    @Test
    void production_は_CHECK_制約で拒否される() {
        FaultInjectionLog prodLog = newLog("SalesMismatchInjector", "production");
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(prodLog),
                "CHECK (environment IN ('dev', 'staging')) で拒否されるはず");
    }

    @Test
    void findByInjectorNameOrderByCreatedAtDesc_で検索できる() {
        repository.saveAndFlush(newLog("SalesMismatchInjector", "dev"));
        repository.saveAndFlush(newLog("InventoryMismatchInjector", "dev"));

        List<FaultInjectionLog> result =
                repository.findByInjectorNameOrderByCreatedAtDesc("SalesMismatchInjector");

        assertEquals(1, result.size());
    }

    private FaultInjectionLog newLog(String injectorName, String environment) {
        FaultInjectionLog log = new FaultInjectionLog();
        log.setInjectorName(injectorName);
        log.setTriggeredAt(LocalDateTime.now());
        log.setTriggeredBy("scheduler");
        log.setEnvironment(environment);
        return log;
    }
}
