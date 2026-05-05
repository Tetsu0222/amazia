package com.example.workflow.repository;

import com.example.workflow.entity.WorkflowRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowRequestRepository extends JpaRepository<WorkflowRequest, Long> {

    List<WorkflowRequest> findByStatusOrderByCreatedAtDesc(String status);

    List<WorkflowRequest> findAllByOrderByCreatedAtDesc();

    boolean existsByTargetTypeAndTargetIdAndStatus(String targetType, Long targetId, String status);
}
