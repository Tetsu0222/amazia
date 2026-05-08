package com.example.batch.service;

import com.example.batch.entity.BatchExecution;
import com.example.batch.repository.BatchExecutionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * フェーズ17 Step 6-0: バッチ実行履歴一覧 Service（設計書 §13.7.1）。
 * Console から /api/console/batch/executions で参照される。
 */
@Service
public class ListBatchExecutionService {

    private final BatchExecutionRepository repository;

    public ListBatchExecutionService(BatchExecutionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResult list(String jobName, String status, int offset, int size) {
        int page = (size > 0) ? offset / size : 0;
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));
        Page<BatchExecution> result = repository.search(emptyToNull(jobName), emptyToNull(status), pageable);
        return new PageResult(result.getContent(), result.getTotalElements(), offset, size);
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    public record PageResult(List<BatchExecution> items, long total, int offset, int size) {}
}
