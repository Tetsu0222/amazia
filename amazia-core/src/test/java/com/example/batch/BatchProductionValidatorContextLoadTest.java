package com.example.batch;

import com.example.batch.config.BatchProductionValidator;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.support.TestPropertySourceUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * フェーズ17 Step 2: BatchProductionValidator が production プロファイルで
 * 危険な設定値が指定された場合に ApplicationContext のロードを失敗させる検証
 * （VALID-1 / VALID-2 / J-6）。
 *
 * <p>{@code @Profile("production")} の検証は本番プロファイル下でしか走らないため、
 * {@link SpringApplication} を手動で起動して production プロファイルを強制する。
 *
 * <p>{@code @SpringBootTest} 経由ではなく手動起動にしているのは、
 * test プロファイル既定値（DataSource など）と production プロファイルが
 * 衝突せず、Validator の検証だけを単独で確かめるため。
 */
class BatchProductionValidatorContextLoadTest {

    @Test
    void VALID_1_fault_injection_有効化なら_production_で_ApplicationContext_が起動失敗する() {
        Map<String, Object> props = new HashMap<>();
        props.put("amazia.simulation.fault-injection.enabled", "true");
        props.put("amazia.batch.bank-transfer-verification.mode", "disabled");

        Throwable thrown = assertThrows(Throwable.class, () -> startContext(props));
        assertTrue(rootCause(thrown).getMessage().contains("fault-injection.enabled"),
                "Validator の例外メッセージが起動失敗の原因に伝播するはず");
    }

    @Test
    void VALID_2_bank_transfer_mode_が_mock_mismatch_rate_なら_production_で_ApplicationContext_が起動失敗する() {
        Map<String, Object> props = new HashMap<>();
        props.put("amazia.simulation.fault-injection.enabled", "false");
        props.put("amazia.batch.bank-transfer-verification.mode", "mock-mismatch-rate");

        Throwable thrown = assertThrows(Throwable.class, () -> startContext(props));
        assertTrue(rootCause(thrown).getMessage().contains("bank-transfer-verification.mode"));
    }

    private ConfigurableApplicationContext startContext(Map<String, Object> props) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.getEnvironment().setActiveProfiles("production");
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                ctx,
                props.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .toArray(String[]::new));
        ctx.register(MinimalConfig.class);
        try {
            ctx.refresh();
            return ctx;
        } catch (RuntimeException e) {
            ctx.close();
            throw e;
        }
    }

    private Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }

    @Configuration
    @Import(BatchProductionValidator.class)
    static class MinimalConfig {
    }
}
