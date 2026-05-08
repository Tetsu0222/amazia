package com.example.batch.repository;

import com.example.batch.entity.BatchExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BatchExecutionRepository extends JpaRepository<BatchExecution, Long> {

    List<BatchExecution> findByStatus(String status);

    List<BatchExecution> findByStatusAndStartedAtBefore(String status, LocalDateTime threshold);

    List<BatchExecution> findByJobNameOrderByStartedAtDesc(String jobName);
}
