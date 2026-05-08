package com.example.batch.controller;

import com.example.batch.service.TriggerBatchManualService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * フェーズ17 Step 2-6 / Step 6-3：Console から手動でオンデマンドジョブを起動するエンドポイント
 * （設計書 §3.4 §8-3 / M-1 / M-4）。
 *
 * <p>本 Controller は規約 1-1（Controller は入出力のみ）に従い、フラグ判定・ジョブ解決・
 * 操作ログ記録は {@link TriggerBatchManualService} に委譲する。
 *
 * <ul>
 *   <li>{@code amazia.batch.manual-trigger-enabled=false} のとき 503</li>
 *   <li>{@code X-User-Id} 未指定は 400（操作ログの user_id NOT NULL 要件）</li>
 *   <li>未登録の jobName は 404（M-4：本番では {@code TriggerFaultInjectionJob} が
 *       {@code @Profile("!production")} で Bean 化されないため自動的に 404 になる）</li>
 *   <li>200 を返す前に {@code operation_logs} へ
 *       {@code screen_name='ConsoleBatchManagementPage'},
 *       {@code api_name='POST /api/console/batch/{job_name}/run'} で記録（独立トランザクション）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/console/batch")
public class BatchManualTriggerController {

    private final TriggerBatchManualService service;

    public BatchManualTriggerController(TriggerBatchManualService service) {
        this.service = service;
    }

    @PostMapping("/{jobName}/run")
    public ResponseEntity<Map<String, Object>> trigger(@PathVariable String jobName,
                                                       @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        service.trigger(jobName, userId);
        return ResponseEntity.ok(Map.of("message", "triggered", "jobName", jobName));
    }
}
