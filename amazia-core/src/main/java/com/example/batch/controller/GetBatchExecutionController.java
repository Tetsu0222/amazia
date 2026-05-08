package com.example.batch.controller;

import com.example.batch.entity.BatchExecution;
import com.example.batch.service.GetBatchExecutionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * フェーズ17 Step 6-0: バッチ実行履歴詳細（設計書 §13.7.1）。
 * GET /api/console/batch/executions/{id}
 */
@RestController
@RequestMapping("/api/console/batch")
public class GetBatchExecutionController {

    private final GetBatchExecutionService service;

    public GetBatchExecutionController(GetBatchExecutionService service) {
        this.service = service;
    }

    @GetMapping("/executions/{id}")
    public Map<String, Object> get(@PathVariable Long id) {
        BatchExecution e = service.get(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("jobName", e.getJobName());
        m.put("status", e.getStatus());
        m.put("startedAt", e.getStartedAt());
        m.put("finishedAt", e.getFinishedAt());
        m.put("targetCount", e.getTargetCount());
        m.put("successCount", e.getSuccessCount());
        m.put("failureCount", e.getFailureCount());
        m.put("errorSummary", e.getErrorSummary());
        m.put("triggeredBy", e.getTriggeredBy());
        m.put("createdAt", e.getCreatedAt());
        return m;
    }
}
