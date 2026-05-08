package com.example.batch.service;

import com.example.batch.config.OnDemandJob;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * フェーズ17 Step 6-3 / 設計書 §13.7 §8-3：
 * Console から手動でオンデマンドジョブを起動する Service。
 *
 * <p>{@link com.example.batch.controller.BatchManualTriggerController} の薄いラッパとして
 * 規約 1-1（Controller は入出力のみ）を満たすため、フラグ判定・ジョブ解決・
 * {@code operation_logs} 記録・実行をまとめてここで担う。
 *
 * <ul>
 *   <li>{@code amazia.batch.manual-trigger-enabled=false} のとき 503</li>
 *   <li>未登録 jobName は 404（M-4：本番では {@code TriggerFaultInjectionJob} が Bean 化されない）</li>
 *   <li>200 を返す前に {@code operation_logs} へ
 *       {@code screen_name='ConsoleBatchManagementPage'},
 *       {@code api_name='POST /api/console/batch/{job_name}/run'} で記録（独立トランザクション）</li>
 * </ul>
 */
@Service
public class TriggerBatchManualService {

    public static final String SCREEN_NAME = "ConsoleBatchManagementPage";
    public static final String ACTION = "trigger_batch_manual";
    public static final String TARGET_TYPE = "batch_jobs";

    private final Map<String, OnDemandJob> onDemandJobs;
    private final OperationLogRepository operationLogRepository;
    private final boolean manualEnabled;

    public TriggerBatchManualService(Map<String, OnDemandJob> onDemandJobs,
                                     OperationLogRepository operationLogRepository,
                                     @Value("${amazia.batch.manual-trigger-enabled:true}") boolean manualEnabled) {
        this.onDemandJobs = onDemandJobs;
        this.operationLogRepository = operationLogRepository;
        this.manualEnabled = manualEnabled;
    }

    /**
     * 手動起動を実行する。例外で 4xx / 5xx を返す方針：
     * <ul>
     *   <li>{@code 503 SERVICE_UNAVAILABLE} ：手動起動が無効</li>
     *   <li>{@code 404 NOT_FOUND} ：jobName が未登録</li>
     * </ul>
     *
     * @param jobName 起動対象ジョブ名（{@link OnDemandJob} Bean のキー）
     * @param userId  起動操作者の {@code users.id}（任意）
     */
    public void trigger(String jobName, Long userId) {
        if (!manualEnabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "manual trigger is disabled");
        }
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "X-User-Id header is required");
        }
        OnDemandJob job = onDemandJobs.get(jobName);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "job not found: " + jobName);
        }

        recordOperationLog(jobName, userId);
        job.run("manual:user_id=" + userId);
    }

    /**
     * 操作ログ記録は独立トランザクション。ジョブ起動例外で巻き戻されないようにする
     * （phase14 の operation_logs 規約・設計書 §8-3 と整合）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void recordOperationLog(String jobName, Long userId) {
        OperationLog log = new OperationLog();
        log.setUserId(userId);
        log.setAction(ACTION);
        log.setTargetType(TARGET_TYPE);
        log.setScreenName(SCREEN_NAME);
        log.setApiName("POST /api/console/batch/" + jobName + "/run");
        log.setComment("manual trigger jobName=" + jobName);
        operationLogRepository.save(log);
    }
}
