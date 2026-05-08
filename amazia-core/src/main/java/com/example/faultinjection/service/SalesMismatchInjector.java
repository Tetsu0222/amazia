package com.example.faultinjection.service;

import com.example.batch.service.BankTransferMockClient;
import com.example.batch.service.RandomGeneratorAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * フェーズ17 Step 5-2: 売上不一致トラブル関数（設計書 §4.2 ① / R-1 / N-6）。
 *
 * <p>{@link BankTransferMockClient} の戻り値層に対して MISMATCH を強制注入するためのフックを提供する。
 * 振込確認は本来 {@code mock-mismatch-rate} モード時に
 * {@code amazia.simulation.fault-injection.sales-mismatch-rate} の確率で MISMATCH を返すが、
 * 本 Injector の {@link #shouldInject()} は同確率で MISMATCH を強制したいタイミング
 * （TriggerFaultInjectionJob からの即時実行）を判定する独立フックである。
 *
 * <p>DB は変更しない（Injector としての副作用は {@code fault_injection_logs} のみ）。
 *
 * <p>{@code @Profile("!production")} により本番プロファイルでは Bean 化されない（五重防御の第 2 層）。
 */
@Profile("!production")
@Component
public class SalesMismatchInjector {

    public static final String INJECTOR_NAME = "SalesMismatchInjector";

    private final RandomGeneratorAdapter random;
    private final FaultInjectionLogger logger;

    @Value("${amazia.simulation.fault-injection.enabled:false}")
    private boolean enabled;

    @Value("${amazia.simulation.fault-injection.sales-mismatch-rate:0.05}")
    private double mismatchRate;

    public SalesMismatchInjector(RandomGeneratorAdapter random, FaultInjectionLogger logger) {
        this.random = random;
        this.logger = logger;
    }

    /**
     * 確率発火判定を行い、抽選に当選したら {@code fault_injection_logs} に履歴を残して
     * {@code true} を返す。{@link BankTransferMockClient} の呼び出し側はこの結果が
     * {@code true} のとき MISMATCH として扱うことができる。
     *
     * <p>{@code amazia.simulation.fault-injection.enabled=false} のときは常に {@code false}。
     */
    public boolean shouldInject() {
        return shouldInject("scheduler");
    }

    public boolean shouldInject(String triggeredBy) {
        if (!enabled) return false;
        if (random.nextDouble() >= mismatchRate) return false;
        logger.log(INJECTOR_NAME, triggeredBy,
                "[fault_injection][sales] forced MISMATCH on bank transfer mock");
        return true;
    }

    /**
     * オンデマンド注入（{@code TriggerFaultInjectionJob} から呼ばれる）。確率を無視して必ず
     * 履歴を残し、MISMATCH 注入が発火したと記録する。
     */
    public void injectOnce(String triggeredBy) {
        logger.log(INJECTOR_NAME, triggeredBy,
                "[fault_injection][sales] forced MISMATCH (manual trigger)");
    }
}
