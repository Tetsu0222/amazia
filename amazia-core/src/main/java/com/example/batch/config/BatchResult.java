package com.example.batch.config;

/**
 * フェーズ17 Step 2-1: バッチ本体の {@code execute()} が返す集計結果。
 *
 * <p>{@link BatchExecutionRecorder} がこの値を {@code batch_executions}
 * の {@code target_count / success_count / failure_count} に転写し、
 * すべて 0 件と区別がつくよう {@code PARTIAL} 判定にも使う。
 *
 * @param targetCount  処理対象件数
 * @param successCount 成功件数
 * @param failureCount 失敗件数（PARTIAL 判定用）
 */
public record BatchResult(int targetCount, int successCount, int failureCount) {

    public static BatchResult empty() {
        return new BatchResult(0, 0, 0);
    }

    public static BatchResult of(int target, int success, int failure) {
        return new BatchResult(target, success, failure);
    }

    public boolean hasFailure() {
        return failureCount > 0;
    }
}
