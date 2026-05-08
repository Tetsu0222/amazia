package com.example.batch.controller;

import com.example.batch.config.OnDemandJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * フェーズ17 Step 2-6: Console から手動でオンデマンドジョブを起動するエンドポイント
 * （設計書 §3.4 / M-1 / M-4）。
 *
 * <ul>
 *   <li>{@code amazia.batch.manual-trigger-enabled=false} のとき 503（Bean は生かしたまま API のみ閉じる）</li>
 *   <li>未登録の jobName は 404。本番では {@code TriggerFaultInjectionJob} が
 *       {@code @Profile("!production")} で Bean 化されないため自動的に 404 になる（M-4）</li>
 *   <li>200 を返した時点でジョブは別途
 *       {@link com.example.batch.config.AbstractBatchJob} の R-7 ロックで多重起動防止される</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/console/batch")
public class BatchManualTriggerController {

    private final Map<String, OnDemandJob> onDemandJobs;
    private final boolean manualEnabled;

    public BatchManualTriggerController(Map<String, OnDemandJob> onDemandJobs,
                                        @Value("${amazia.batch.manual-trigger-enabled:true}") boolean manualEnabled) {
        this.onDemandJobs = onDemandJobs;
        this.manualEnabled = manualEnabled;
    }

    @PostMapping("/{jobName}/run")
    public ResponseEntity<Map<String, Object>> trigger(@PathVariable String jobName,
                                                       @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (!manualEnabled) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "manual trigger is disabled"));
        }
        OnDemandJob job = onDemandJobs.get(jobName);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "job not found: " + jobName));
        }
        job.run("manual:user_id=" + (userId != null ? userId : "unknown"));
        return ResponseEntity.ok(Map.of("message", "triggered", "jobName", jobName));
    }
}
