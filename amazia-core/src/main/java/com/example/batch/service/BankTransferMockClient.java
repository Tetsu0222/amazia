package com.example.batch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * フェーズ17 Step 3-3: 振込確認の疑似 API（設計書 §3.1 ③ R-1 / N-6）。
 *
 * <p>{@code amazia.batch.bank-transfer-verification.mode} で振る舞いを切り替える：
 * <ul>
 *   <li>{@code disabled}（本番既定）：このメソッドが呼ばれた時点で {@link Mode#DISABLED} を返す。
 *       呼び出し側は MISMATCH の再判定をしない</li>
 *   <li>{@code mock-match}：常に {@link Result#MATCH}</li>
 *   <li>{@code mock-mismatch-rate}：{@code amazia.simulation.fault-injection.sales-mismatch-rate}
 *       の確率で {@link Result#MISMATCH}（{@code SIMULATION_FAULT_INJECTION=true} のときのみ意味がある）</li>
 * </ul>
 *
 * <p>本番で {@code mock-mismatch-rate} に設定された場合は
 * {@link com.example.batch.config.BatchProductionValidator} が起動失敗で物理的に拒否する（J-6）。
 */
@Component
public class BankTransferMockClient {

    public enum Mode { DISABLED, MOCK_MATCH, MOCK_MISMATCH_RATE }

    public enum Result { MATCH, MISMATCH, DISABLED }

    private final RandomGeneratorAdapter random;

    @Value("${amazia.batch.bank-transfer-verification.mode:disabled}")
    private String configuredMode;

    @Value("${amazia.simulation.fault-injection.sales-mismatch-rate:0.05}")
    private double mismatchRate;

    public BankTransferMockClient(RandomGeneratorAdapter random) {
        this.random = random;
    }

    public Mode mode() {
        return switch (configuredMode) {
            case "mock-match" -> Mode.MOCK_MATCH;
            case "mock-mismatch-rate" -> Mode.MOCK_MISMATCH_RATE;
            default -> Mode.DISABLED;
        };
    }

    /**
     * 振込確認を実施する。
     *
     * @return {@link Result#DISABLED} なら呼び出し側は照合をスキップする
     */
    public Result verify() {
        switch (mode()) {
            case DISABLED -> { return Result.DISABLED; }
            case MOCK_MATCH -> { return Result.MATCH; }
            case MOCK_MISMATCH_RATE -> {
                return random.nextDouble() < mismatchRate ? Result.MISMATCH : Result.MATCH;
            }
            default -> { return Result.DISABLED; }
        }
    }
}
