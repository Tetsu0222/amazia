package com.example.batch.config;

/**
 * フェーズ17 Step 2-6: 手動起動可能ジョブのマーカーインタフェース（設計書 §3.4 / M-1）。
 *
 * <p>{@link AbstractBatchJob} を継承するジョブのうち Console から起動できるものは
 * 本インタフェースを併せて実装し、{@code @Component("<jobName>")} で Bean 名を
 * {@link AbstractBatchJob#jobName()} と一致させる。
 * {@link BatchManualTriggerController} は {@code Map<String, OnDemandJob>}
 * を受け取り Bean 名で解決する。
 *
 * <p>K-6: オンデマンドジョブはステートレス（インスタンスフィールドで状態を持たない）として
 * 実装する。長期 OFF → ON 切替時の副作用を排除するため。
 */
public interface OnDemandJob {

    /** Bean 名 / batch_executions.job_name と一致させる。 */
    String jobName();

    /** Console / Scheduler 共通エントリ。{@link AbstractBatchJob#run(String)} に委譲する。 */
    void run(String triggeredBy);
}
