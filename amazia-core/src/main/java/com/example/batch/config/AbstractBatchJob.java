package com.example.batch.config;

import com.example.batch.entity.BatchExecution;
import com.example.batch.service.BatchExecutionRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * フェーズ17 Step 2-1: 全バッチ共通のテンプレートメソッド（設計書 §3 共通制御）。
 *
 * <p>サブクラスは {@link #jobName()} と {@link #execute()} のみを実装する。
 * 多重起動防止 / RUNNING レコード作成 / リトライ / ステータス更新 / ロック解放は
 * 本クラスが一手に担い、サブクラスは業務処理に集中する。
 *
 * <p>本クラス自体は {@code @Component} を付けず、サブクラスがそれぞれ
 * {@code @Component} で Bean 登録する前提（オンデマンドジョブの Bean 名 = jobName / M-1）。
 *
 * <p>関連：
 * <ul>
 *   <li>R-7: {@link BatchJobLockRegistry} で多重起動防止</li>
 *   <li>R-6: {@link BatchRetryClassifier} でリトライ可否を判定</li>
 *   <li>1: {@link BatchExecutionRecorder} が REQUIRES_NEW で状態を残す</li>
 * </ul>
 */
public abstract class AbstractBatchJob {

    private static final Logger log = LoggerFactory.getLogger(AbstractBatchJob.class);

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 1000L;

    @Autowired private BatchExecutionRecorder recorder;
    @Autowired private BatchJobLockRegistry lockRegistry;
    @Autowired private BatchRetryClassifier retryClassifier;

    /** ジョブ識別子。Bean 名 / batch_executions.job_name と一致させる。 */
    protected abstract String jobName();

    /** 実際のバッチ処理本体。集計結果を {@link BatchResult} で返す。 */
    protected abstract BatchResult execute() throws Exception;

    /**
     * バッチ起動エントリ。R-7 ロック → RUNNING 記録 → リトライ実行 → 終端ステータス更新。
     *
     * @param triggeredBy {@code "scheduler"} または {@code "manual:user_id=N"}
     */
    public final void run(String triggeredBy) {
        if (!lockRegistry.tryAcquire(jobName())) {
            log.info("[{}] skip: another instance is running", jobName());
            return;
        }
        BatchExecution exec = recorder.start(jobName(), triggeredBy);
        try {
            BatchResult result = runWithRetry();
            recorder.success(exec, result);
        } catch (Exception e) {
            log.error("[{}] failed", jobName(), e);
            recorder.failure(exec, e);
        } finally {
            lockRegistry.release(jobName());
        }
    }

    private BatchResult runWithRetry() throws Exception {
        int attempt = 0;
        long delayMs = INITIAL_BACKOFF_MS;
        while (true) {
            try {
                return execute();
            } catch (Exception e) {
                attempt++;
                if (!retryClassifier.shouldRetry(e) || attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                log.warn("[{}] retry {}/{} after {}ms (cause={})",
                        jobName(), attempt, MAX_ATTEMPTS - 1, delayMs, e.toString());
                Thread.sleep(delayMs);
                delayMs *= 2;
            }
        }
    }
}
