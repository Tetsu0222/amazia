package com.example.batch;

import com.example.batch.config.BatchProductionValidator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * フェーズ17 Step 1 / Step 2: BatchProductionValidator の起動失敗テスト（J-6 / VALID-1 / VALID-2）。
 *
 * <p>本クラスは {@code @Profile("production")} で本番プロファイルでのみ Bean 化される。
 * 本テストでは Bean を直接 new し、フィールド注入を {@link ReflectionTestUtils} で
 * シミュレートしたうえで {@code validate()} を呼び、危険値で
 * {@link IllegalStateException} が投げられることを確認する。
 *
 * <p>{@code @SpringBootTest(properties=...)} ベースの ApplicationContext ロード失敗テストは
 * Step 2 で {@link BatchProductionValidator} が Step 2 の他コンポーネント
 * （AbstractBatchJob 等）と一緒に登録される段階で追加する。
 */
class BatchProductionValidatorTest {

    @Test
    void VALID_1_fault_injection_有効化なら起動時に_IllegalStateException() {
        BatchProductionValidator validator = new BatchProductionValidator();
        ReflectionTestUtils.setField(validator, "faultInjectionEnabled", true);
        ReflectionTestUtils.setField(validator, "bankTransferMode", "disabled");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(validator, "validate"));
        assertTrue(ex.getMessage().contains("fault-injection.enabled"));
    }

    @Test
    void VALID_2_bank_transfer_mode_が_mock_mismatch_rate_なら起動時に_IllegalStateException() {
        BatchProductionValidator validator = new BatchProductionValidator();
        ReflectionTestUtils.setField(validator, "faultInjectionEnabled", false);
        ReflectionTestUtils.setField(validator, "bankTransferMode", "mock-mismatch-rate");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(validator, "validate"));
        assertTrue(ex.getMessage().contains("bank-transfer-verification.mode"));
    }

    @Test
    void 安全な組合せでは何も投げない() {
        BatchProductionValidator validator = new BatchProductionValidator();
        ReflectionTestUtils.setField(validator, "faultInjectionEnabled", false);
        ReflectionTestUtils.setField(validator, "bankTransferMode", "disabled");

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(validator, "validate"));
    }

    @Test
    void mock_match_は_本番でも許容() {
        BatchProductionValidator validator = new BatchProductionValidator();
        ReflectionTestUtils.setField(validator, "faultInjectionEnabled", false);
        ReflectionTestUtils.setField(validator, "bankTransferMode", "mock-match");

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(validator, "validate"));
    }
}
