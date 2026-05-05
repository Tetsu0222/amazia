package com.example.workflow.service;

import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import com.example.workflow.config.WorkflowRoleHierarchy;
import com.example.workflow.config.WorkflowStatus;
import com.example.workflow.dto.WorkflowResponse;
import com.example.workflow.entity.WorkflowRequest;
import com.example.workflow.entity.WorkflowRequestDetail;
import com.example.workflow.repository.WorkflowRequestDetailRepository;
import com.example.workflow.repository.WorkflowRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class RejectWorkflowStepService {

    private final WorkflowRequestRepository requestRepository;
    private final WorkflowRequestDetailRepository detailRepository;
    private final UserRepository userRepository;
    private final WorkflowRoleHierarchy roleHierarchy;

    public RejectWorkflowStepService(WorkflowRequestRepository requestRepository,
                                     WorkflowRequestDetailRepository detailRepository,
                                     UserRepository userRepository,
                                     WorkflowRoleHierarchy roleHierarchy) {
        this.requestRepository = requestRepository;
        this.detailRepository  = detailRepository;
        this.userRepository    = userRepository;
        this.roleHierarchy     = roleHierarchy;
    }

    @Transactional
    public WorkflowResponse reject(Long workflowId, Integer stepNumber,
                                   Long approverUserId, String approverRole) {
        WorkflowRequest req = requestRepository.findById(workflowId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found"));

        if (!WorkflowStatus.PENDING.equals(req.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Workflow is not pending (status=" + req.getStatus() + ")");
        }

        List<WorkflowRequestDetail> stepDetails =
            detailRepository.findByWorkflowRequestsIdAndStepNumber(workflowId, stepNumber);
        if (stepDetails.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Step not found: " + stepNumber);
        }

        WorkflowRequestDetail target = pickAssignedDetail(stepDetails, approverUserId, approverRole);
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You are not allowed to reject this step");
        }

        User approver = userRepository.findById(approverUserId).orElse(null);
        target.setApproverUserId(approverUserId);
        target.setApproverName(approver != null ? approver.getName() : null);
        target.setStatus(WorkflowStatus.REJECTED);
        detailRepository.save(target);

        // 設計書 5.3：他の pending detail はそのまま waiting に戻す（承認不要扱い）
        for (WorkflowRequestDetail d : stepDetails) {
            if (Objects.equals(d.getId(), target.getId())) continue;
            if (WorkflowStatus.PENDING.equals(d.getStatus())) {
                d.setStatus(WorkflowStatus.WAITING);
                detailRepository.save(d);
            }
        }

        // 親を rejected にして反映処理は呼ばない（completed_at は反映処理がないので即時セット）
        req.setStatus(WorkflowStatus.REJECTED);
        req.setCompletedAt(LocalDateTime.now());
        requestRepository.save(req);

        return WorkflowResponse.from(
            req,
            detailRepository.findByWorkflowRequestsIdOrderByStepNumberAscIdAsc(workflowId)
        );
    }

    private WorkflowRequestDetail pickAssignedDetail(List<WorkflowRequestDetail> stepDetails,
                                                     Long approverUserId, String approverRole) {
        for (WorkflowRequestDetail d : stepDetails) {
            if (!WorkflowStatus.PENDING.equals(d.getStatus())) continue;
            if (Objects.equals(d.getDestinationUserId(), approverUserId)) {
                return d;
            }
        }
        for (WorkflowRequestDetail d : stepDetails) {
            if (!WorkflowStatus.PENDING.equals(d.getStatus())) continue;
            if (d.getDestinationUserId() != null) continue;
            if (d.getTargetRole().equals(approverRole)) {
                return d;
            }
        }
        if (roleHierarchy.isEternalAdvisor(approverRole)) {
            for (WorkflowRequestDetail d : stepDetails) {
                if (!WorkflowStatus.PENDING.equals(d.getStatus())) continue;
                if (d.getDestinationUserId() == null) {
                    return d;
                }
            }
        }
        return null;
    }
}
