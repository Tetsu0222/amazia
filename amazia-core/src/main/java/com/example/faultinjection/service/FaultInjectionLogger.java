package com.example.faultinjection.service;

import com.example.faultinjection.entity.FaultInjectionLog;
import com.example.faultinjection.repository.FaultInjectionLogRepository;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * フェーズ17 Step 5-1: フォルトインジェクション発火履歴を {@code fault_injection_logs} に
 * 記録する共通 Service（設計書 §4.1 第 3 段防御 / §5.3）。
 *
 * <p>{@code environment} は {@link Environment#getActiveProfiles()} から導出する。
 * {@code prod} / {@code production} 系プロファイルを除外し、{@code dev} / {@code staging} の
 * いずれかを採用する（DB CHECK 制約を必ず通る値を返す）。万一どちらにも該当しない
 * プロファイル（例：test 単独）の場合は {@code dev} を採用する。
 *
 * <p>記録は {@link Propagation#REQUIRES_NEW} で独立トランザクションに分離し、
 * 注入実体（Repository 直接書き込み等）のロールバックに巻き込まれないようにする。
 */
@Service
public class FaultInjectionLogger {

    private final FaultInjectionLogRepository repository;
    private final Environment environment;

    public FaultInjectionLogger(FaultInjectionLogRepository repository, Environment environment) {
        this.repository = repository;
        this.environment = environment;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FaultInjectionLog log(String injectorName, String triggeredBy, String targetSummary) {
        FaultInjectionLog entity = new FaultInjectionLog();
        entity.setInjectorName(injectorName);
        entity.setTriggeredAt(LocalDateTime.now());
        entity.setTriggeredBy(triggeredBy != null ? triggeredBy : "scheduler");
        entity.setEnvironment(resolveEnvironment());
        entity.setTargetSummary(targetSummary);
        return repository.saveAndFlush(entity);
    }

    /**
     * 現在の active profile から {@code dev} / {@code staging} のいずれかを返す。
     * {@code production} / {@code prod} を含む場合は {@link IllegalStateException} を投げて
     * 早期失敗させる（DB CHECK で拒否される前にコード層で停止させる安全装置）。
     */
    public String resolveEnvironment() {
        String[] profiles = environment.getActiveProfiles();
        for (String p : profiles) {
            if ("production".equalsIgnoreCase(p) || "prod".equalsIgnoreCase(p)) {
                throw new IllegalStateException(
                        "[FaultInjectionLogger] fault injection must not run in production profile");
            }
        }
        for (String p : profiles) {
            if ("staging".equalsIgnoreCase(p)) return "staging";
        }
        for (String p : profiles) {
            if ("dev".equalsIgnoreCase(p)) return "dev";
        }
        return "dev";
    }
}
