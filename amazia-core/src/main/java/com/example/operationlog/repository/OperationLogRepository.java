package com.example.operationlog.repository;

import com.example.operationlog.entity.OperationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {
    List<OperationLog> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<OperationLog> findByTargetTypeAndTargetId(String targetType, Long targetId);
    List<OperationLog> findByActionOrderByCreatedAtDesc(String action);
}
