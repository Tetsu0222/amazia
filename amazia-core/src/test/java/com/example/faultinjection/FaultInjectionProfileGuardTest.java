package com.example.faultinjection;

import com.example.batch.entity.BatchExecution;
import com.example.batch.job.TriggerFaultInjectionJob;
import com.example.batch.repository.BatchExecutionRepository;
import com.example.faultinjection.entity.FaultInjectionLog;
import com.example.faultinjection.repository.FaultInjectionLogRepository;
import com.example.faultinjection.service.DeliveryTroubleInjector;
import com.example.faultinjection.service.InventoryMismatchInjector;
import com.example.faultinjection.service.SalesMismatchInjector;
import com.example.shared.config.TestAwsConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 5 完了条件の検証（設計書 §6-6）。
 *
 * <ul>
 *   <li>FPG_1：本番プロファイルでは Injector / TriggerFaultInjectionJob の Bean が存在しない
 *       ({@link NoSuchBeanDefinitionException})</li>
 *   <li>FPG_2：DB CHECK 制約により {@code environment='production'} の直接 INSERT は拒否</li>
 *   <li>FPG_3：non-production プロファイル（test）では 4 Bean がそろって登録されている</li>
 * </ul>
 *
 * <p>本番 ApplicationContext を立ち上げると BatchProductionValidator や
 * 他コンポーネントの DataSource が衝突するため、Bean 不在は
 * {@code @Profile("production")} を持つ確認用 Marker Bean が test プロファイル下で
 * 登録されないことの裏返しと、Spring が {@code @Profile("!production")} 解釈をして
 * いることを示す形で確認する（ここでは test プロファイル下で実行）。
 *
 * <p>具体的には：(a) 4 Bean は test プロファイルで取得できる、
 * (b) "non-existent in test" の Marker は test プロファイル下では取得できない、
 * (c) DB CHECK は environment='production' を直接拒否する、の 3 観点で代替検証する。
 * production プロファイル下での実起動は {@link com.example.batch.BatchProductionValidatorContextLoadTest}
 * と整合する形で、別途運用環境でしか走らない。
 */
@SpringBootTest
@Import({TestAwsConfig.class, FaultInjectionProfileGuardTest.ProductionOnlyMarker.class})
@ActiveProfiles("test")
class FaultInjectionProfileGuardTest {

    @Autowired private ApplicationContext context;
    @Autowired private FaultInjectionLogRepository repository;
    @PersistenceContext private EntityManager em;

    @Test
    void FPG_1_test_プロファイルでは_4_つの非本番限定_Bean_が登録されている() {
        // @Profile("!production") を持つ Bean は test では登録される
        assertNotNull(context.getBean(SalesMismatchInjector.class));
        assertNotNull(context.getBean(InventoryMismatchInjector.class));
        assertNotNull(context.getBean(DeliveryTroubleInjector.class));
        assertNotNull(context.getBean(TriggerFaultInjectionJob.class));
    }

    @Test
    void FPG_2_production_限定_Marker_Bean_は_test_プロファイルでは登録されない() {
        // @Profile("production") を持つ Marker は test では登録されない。
        // これにより Spring の Profile 解釈が機能していることを保証する
        assertThrows(NoSuchBeanDefinitionException.class,
                () -> context.getBean(ProductionOnlyMarker.class));
    }

    @Test
    void FPG_3_DB_CHECK_で_production_の_INSERT_が拒否される() {
        FaultInjectionLog log = new FaultInjectionLog();
        log.setInjectorName("FPG_DirectProd");
        log.setTriggeredAt(LocalDateTime.now());
        log.setTriggeredBy("scheduler");
        log.setEnvironment("production");
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(log));
    }

    @Test
    void FPG_4_TriggerFaultInjectionJob_の_Bean_名が_jobName_と一致する() {
        // BatchManualTriggerController の Map<String, OnDemandJob> はこの一致を前提にする
        Object bean = context.getBean(TriggerFaultInjectionJob.JOB_NAME);
        assertNotNull(bean);
        assertTrue(bean instanceof TriggerFaultInjectionJob);
    }

    @Test
    void FPG_5_BatchExecutionRepository_は_test_でも利用可能_他の依存が壊れていない_sanity() {
        // 既存の Step 2 基盤が健全なまま Step 5 を載せていることのサニティチェック
        BatchExecutionRepository ber = context.getBean(BatchExecutionRepository.class);
        assertNotNull(ber);
    }

    /**
     * production プロファイル下でのみ Bean 化される確認用 Marker。
     * test プロファイル下では Spring が {@code @Profile("production")} を不一致と判定して
     * Bean 化しないことの裏返しで、Profile 解釈が効いていることを示す。
     */
    @Profile("production")
    @Component
    static class ProductionOnlyMarker {
        public String name() { return "production-only"; }
    }
}
