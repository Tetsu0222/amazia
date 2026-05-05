package com.example.workflow.service;

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
public class CancelWorkflowRequestService {

    private final WorkflowRequestRepository requestRepository;
    private final WorkflowRequestDetailRepository detailRepository;
    private final WorkflowRoleHierarchy roleHierarchy;

    public CancelWorkflowRequestService(WorkflowRequestRepository requestRepository,
                                        WorkflowRequestDetailRepository detailRepository,
                                        WorkflowRoleHierarchy roleHierarchy) {
        this.requestRepository = requestRepository;
        this.detailRepository  = detailRepository;
        this.roleHierarchy     = roleHierarchy;
    }

    @Transactional
    public WorkflowResponse cancel(Long workflowId, Long actorUserId, String actorRole) {
        WorkflowRequest req = requestRepository.findById(workflowId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found"));

        if (!WorkflowStatus.PENDING.equals(req.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Workflow is not pending (status=" + req.getStatus() + ")");
        }

        // 申請者本人 or 承認権限者（admin系・eternal）が取り下げ可
        boolean isRequester = Objects.equals(req.getRequestedBy(), actorUserId);
        boolean isAuthorized = roleHierarchy.canApprove(actorRole);
        if (!isRequester && !isAuthorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You are not allowed to cancel this workflow");
        }

        List<WorkflowRequestDetail> details =
            detailRepository.findByWorkflowRequestsIdOrderByStepNumberAscIdAsc(workflowId);
        for (WorkflowRequestDetail d : details) {
            d.setStatus(WorkflowStatus.CANCELED);
            detailRepository.save(d);
        }

        req.setStatus(WorkflowStatus.CANCELED);
        req.setCompletedAt(LocalDateTime.now());
        requestRepository.save(req);

        return WorkflowResponse.from(req, details);
    }
}
