package com.example.batch.e2e;

import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * phase17 Step 8 / E2E-8（設計書 §12.3 / M-4）：
 * 本番プロファイルでは {@code TriggerFaultInjectionJob} が {@code @Profile("!production")} で
 * Bean 化されないため、{@code POST /api/console/batch/TriggerFaultInjectionJob/run} が
 * {@code Map<String, OnDemandJob>} ルックアップで {@code null} を返し HTTP 404 を返す。
 *
 * <p>本テストは {@code @ActiveProfiles("production-mvc-only")} の薄い専用プロファイルで動かす
 * のではなく、{@code @ActiveProfiles("production")} を使う。{@code BatchProductionValidator} が
 * 本番プロファイルで「{@code SIMULATION_FAULT_INJECTION=true}」「mock-mismatch-rate」を拒否するため、
 * これらは既定値（false / disabled）で起動する必要がある。
 *
 * <p>本番プロファイル下で {@code @SpringBootTest} を立てると DataSource / Flyway 等本番依存の
 * Bean が要求される懸念があるが、test プロファイル併用で test 用の H2 DataSource を継承する。
 */
@SpringBootTest(properties = {
        // 本番 Validator が拒否する 2 軸を既定値に固定（J-6 整合）
        "amazia.simulation.fault-injection.enabled=false",
        "amazia.batch.bank-transfer-verification.mode=disabled",
        // production プロファイル下でも H2 を使うため test プロファイル相当の設定を流用
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@Import(TestAwsConfig.class)
@ActiveProfiles({"test", "production"})
class TriggerFaultInjectionProductionE2ETest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationContext context;

    @Test
    void E2E_8_本番プロファイルでは_TriggerFaultInjectionJob_Bean_が登録されない() {
        // @Profile("!production") の効果：Bean マップに居ない
        assertThrows(NoSuchBeanDefinitionException.class,
                () -> context.getBean("TriggerFaultInjectionJob"),
                "@Profile('!production') により TriggerFaultInjectionJob Bean は非登録");
    }

    @Test
    void E2E_8_本番プロファイル下で_POST_TriggerFaultInjectionJob_run_は_404_を返す() throws Exception {
        mockMvc.perform(post("/api/console/batch/TriggerFaultInjectionJob/run")
                        .header("X-User-Id", "1"))
                .andExpect(status().isNotFound());
    }
}
