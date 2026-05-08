package com.example.faultinjection;

import com.example.faultinjection.entity.FaultInjectionLog;
import com.example.faultinjection.repository.FaultInjectionLogRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 1: fault_injection_logs Entity / Repository の永続化検証。
 * 五重防御の DB CHECK 層 chk_fault_logs_no_prod が effective であることを確認する。
 */
@SpringBootTest
@Import(TestAwsConfig.class)
@ActiveProfiles("test")
@Transactional
class FaultInjectionLogRepositoryTest {

    @Autowired
    private FaultInjectionLogRepository repository;

    @BeforeEach
    void cleanupPriorLogs() {
        // FaultInjectionLogger は REQUIRES_NEW で独立コミットするため、
        // 他テスト（TriggerFaultInjectionJobTest / SalesMismatchInjectorTest 等）の
        // injector 発火がテストロールバックを貫通して fault_injection_logs に残る。
        // 件数アサーションを行う本テストの直前に「SalesMismatchInjector」「InventoryMismatchInjector」名の
        // 残置を掃除する自衛コード（051 派生②の続き）。クラス @Transactional 内なので
        // ロールバック対象だが、auto-flush 後の find クエリには反映されるので件数検証は安定する。
        repository.deleteAll(repository.findByInjectorNameOrderByCreatedAtDesc("SalesMismatchInjector"));
        repository.deleteAll(repository.findByInjectorNameOrderByCreatedAtDesc("InventoryMismatchInjector"));
        repository.deleteAll(repository.findByInjectorNameOrderByCreatedAtDesc("DeliveryTroubleInjector"));
    }

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
