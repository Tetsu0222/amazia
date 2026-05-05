package com.example.workflow.repository;

import com.example.workflow.entity.WorkflowRequestDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowRequestDetailRepository extends JpaRepository<WorkflowRequestDetail, Long> {

    List<WorkflowRequestDetail> findByWorkflowRequestsIdOrderByStepNumberAscIdAsc(Long workflowRequestsId);

    List<WorkflowRequestDetail> findByWorkflowRequestsIdAndStepNumber(Long workflowRequestsId, Integer stepNumber);
}
