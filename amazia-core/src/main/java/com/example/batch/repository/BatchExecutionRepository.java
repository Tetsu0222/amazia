package com.example.batch.repository;

import com.example.batch.entity.BatchExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BatchExecutionRepository extends JpaRepository<BatchExecution, Long> {

    List<BatchExecution> findByStatus(String status);

    List<BatchExecution> findByStatusAndStartedAtBefore(String status, LocalDateTime threshold);

    List<BatchExecution> findByJobNameOrderByStartedAtDesc(String jobName);

    /**
     * Step 6-0: Console 履歴一覧取得用。jobName / status は NULL のときフィルタしない。
     */
    @Query("""
            SELECT b FROM BatchExecution b
             WHERE (:jobName IS NULL OR b.jobName = :jobName)
               AND (:status IS NULL OR b.status = :status)
            """)
    Page<BatchExecution> search(@Param("jobName") String jobName,
                                @Param("status") String status,
                                Pageable pageable);
}
