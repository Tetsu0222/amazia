package com.example.batch.service;

import com.example.batch.entity.BatchExecution;
import com.example.batch.repository.BatchExecutionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * フェーズ17 Step 6-0: バッチ実行履歴詳細 Service（設計書 §13.7.1）。
 */
@Service
public class GetBatchExecutionService {

    private final BatchExecutionRepository repository;

    public GetBatchExecutionService(BatchExecutionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public BatchExecution get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "batch execution not found: " + id));
    }
}
