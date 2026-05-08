package com.example.batch.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 本番プロファイル限定の起動時 Validator（J-6 / 設計書 §7 末尾チェックリスト）。
 *
 * <p>「フォルトインジェクション五重防御」の起動時防衛層。
 * 危険な設定値が production プロファイルで指定された場合、
 * {@link IllegalStateException} を投げて {@code ApplicationContext} の
 * 起動を強制失敗させる。これにより：
 * <ul>
 *   <li>{@code SIMULATION_FAULT_INJECTION=true} が production で有効化されない</li>
 *   <li>{@code BANK_TRANSFER_VERIFICATION_MODE=mock-mismatch-rate} が production で起動しない</li>
 * </ul>
 *
 * <p>テスト：{@code BatchProductionValidatorTest}（J-6 / VALID-1 / VALID-2）。
 */
@Component
@Profile("production")
public class BatchProductionValidator {

    @Value("${amazia.simulation.fault-injection.enabled:false}")
    private boolean faultInjectionEnabled;

    @Value("${amazia.batch.bank-transfer-verification.mode:disabled}")
    private String bankTransferMode;

    @PostConstruct
    void validate() {
        if (faultInjectionEnabled) {
            throw new IllegalStateException(
                    "[BatchProductionValidator] amazia.simulation.fault-injection.enabled must be false in production");
        }
        if ("mock-mismatch-rate".equals(bankTransferMode)) {
            throw new IllegalStateException(
                    "[BatchProductionValidator] amazia.batch.bank-transfer-verification.mode must NOT be mock-mismatch-rate in production");
        }
    }
}
