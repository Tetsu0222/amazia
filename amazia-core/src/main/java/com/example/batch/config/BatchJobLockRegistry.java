package com.example.batch.config;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * フェーズ17 Step 2-3: JVM 内 {@link ConcurrentHashMap} ベースのバッチ多重起動防止ロック
 * （設計書 §3 共通制御 R-7）。
 *
 * <p>{@code SELECT ... FOR UPDATE} は採用せず、単一 EC2 + 単一 JVM 前提のため
 * {@link AtomicBoolean#compareAndSet} だけでロックを実現する。複数 JVM が
 * 立ち上がった瞬間に効力を失うが、本フェーズではスコープ外（13.4）。
 */
@Component
public class BatchJobLockRegistry {

    private final ConcurrentHashMap<String, AtomicBoolean> locks = new ConcurrentHashMap<>();

    /**
     * 指定ジョブ名のロックを取得する。既に保持中なら {@code false}。
     */
    public boolean tryAcquire(String jobName) {
        AtomicBoolean lock = locks.computeIfAbsent(jobName, k -> new AtomicBoolean(false));
        return lock.compareAndSet(false, true);
    }

    /**
     * 指定ジョブ名のロックを解放する。未取得状態でも安全に呼べる。
     */
    public void release(String jobName) {
        locks.computeIfPresent(jobName, (k, v) -> {
            v.set(false);
            return v;
        });
    }
}
