package com.example.batch.controller;

import com.example.batch.entity.BatchExecution;
import com.example.batch.service.ListBatchExecutionService;
import com.example.batch.service.ListBatchExecutionService.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * フェーズ17 Step 6-0: バッチ実行履歴一覧（設計書 §13.7.1）。
 * GET /api/console/batch/executions
 */
@RestController
@RequestMapping("/api/console/batch")
public class ListBatchExecutionController {

    private final ListBatchExecutionService service;

    public ListBatchExecutionController(ListBatchExecutionService service) {
        this.service = service;
    }

    @GetMapping("/executions")
    public Map<String, Object> list(
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int size) {
        PageResult result = service.list(jobName, status, offset, size);
        return toResponse(result.items(), result.total(), result.offset(), result.size());
    }

    private static Map<String, Object> toResponse(List<BatchExecution> items, long total,
                                                  int offset, int size) {
        List<Map<String, Object>> mapped = items.stream().map(ListBatchExecutionController::toMap).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", mapped);
        body.put("total", total);
        body.put("offset", offset);
        body.put("size", size);
        return body;
    }

    private static Map<String, Object> toMap(BatchExecution e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("jobName", e.getJobName());
        m.put("status", e.getStatus());
        m.put("startedAt", e.getStartedAt());
        m.put("finishedAt", e.getFinishedAt());
        m.put("targetCount", e.getTargetCount());
        m.put("successCount", e.getSuccessCount());
        m.put("failureCount", e.getFailureCount());
        m.put("triggeredBy", e.getTriggeredBy());
        return m;
    }
}
