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
import java.util.Optional;

@Service
public class ApproveWorkflowStepService {

    private final WorkflowRequestRepository requestRepository;
    private final WorkflowRequestDetailRepository detailRepository;
    private final UserRepository userRepository;
    private final ApplyWorkflowService applyWorkflowService;
    private final WorkflowRoleHierarchy roleHierarchy;

    public ApproveWorkflowStepService(WorkflowRequestRepository requestRepository,
                                      WorkflowRequestDetailRepository detailRepository,
                                      UserRepository userRepository,
                                      ApplyWorkflowService applyWorkflowService,
                                      WorkflowRoleHierarchy roleHierarchy) {
        this.requestRepository    = requestRepository;
        this.detailRepository     = detailRepository;
        this.userRepository       = userRepository;
        this.applyWorkflowService = applyWorkflowService;
        this.roleHierarchy        = roleHierarchy;
    }

    @Transactional
    public WorkflowResponse approve(Long workflowId, Integer stepNumber,
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

        // 該当ステップが pending（=現アクティブステップ）でなければ承認不可
        boolean stepActive = stepDetails.stream()
            .anyMatch(d -> WorkflowStatus.PENDING.equals(d.getStatus()));
        if (!stepActive) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Step " + stepNumber + " is not active");
        }

        WorkflowRequestDetail target = pickAssignedDetail(stepDetails, approverUserId, approverRole);
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You are not allowed to approve this step");
        }

        User approver = userRepository.findById(approverUserId).orElse(null);
        target.setApproverUserId(approverUserId);
        target.setApproverName(approver != null ? approver.getName() : null);
        target.setStatus(WorkflowStatus.APPROVED);
        detailRepository.save(target);

        // 同一 step が全て approved なら次ステップを開始 / 全 step 完了なら反映
        boolean stepAllApproved = detailRepository
            .findByWorkflowRequestsIdAndStepNumber(workflowId, stepNumber).stream()
            .allMatch(d -> WorkflowStatus.APPROVED.equals(d.getStatus()));

        if (stepAllApproved) {
            Optional<Integer> nextStep = findNextStep(workflowId, stepNumber);
            if (nextStep.isPresent()) {
                List<WorkflowRequestDetail> nextDetails =
                    detailRepository.findByWorkflowRequestsIdAndStepNumber(workflowId, nextStep.get());
                for (WorkflowRequestDetail d : nextDetails) {
                    if (WorkflowStatus.WAITING.equals(d.getStatus())) {
                        d.setStatus(WorkflowStatus.PENDING);
                        detailRepository.save(d);
                    }
                }
            } else {
                // 全ステップ承認 → 反映 → completed_at セット
                applyWorkflowService.apply(req.getTargetType(), req.getTargetId(), req.getPayload());
                req.setStatus(WorkflowStatus.APPROVED);
                req.setCompletedAt(LocalDateTime.now());
                requestRepository.save(req);
            }
        }

        return WorkflowResponse.from(
            req,
            detailRepository.findByWorkflowRequestsIdOrderByStepNumberAscIdAsc(workflowId)
        );
    }

    /**
     * 自分が承認できる pending detail を1件選ぶ。
     * - eternal_advisor は target_role 不問で全 pending detail を承認可
     * - destination_user_id 指定がある detail は本人のみ
     * - destination_user_id == null の detail は同 target_role なら誰でも
     */
    private WorkflowRequestDetail pickAssignedDetail(List<WorkflowRequestDetail> stepDetails,
                                                     Long approverUserId, String approverRole) {
        // 1. 個別指定が自分のもの
        for (WorkflowRequestDetail d : stepDetails) {
            if (!WorkflowStatus.PENDING.equals(d.getStatus())) continue;
            if (Objects.equals(d.getDestinationUserId(), approverUserId)) {
                return d;
            }
        }
        // 2. ロール一致（destination_user_id が NULL）
        for (WorkflowRequestDetail d : stepDetails) {
            if (!WorkflowStatus.PENDING.equals(d.getStatus())) continue;
            if (d.getDestinationUserId() != null) continue;
            if (d.getTargetRole().equals(approverRole)) {
                return d;
            }
        }
        // 3. eternal_advisor 特権
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

    private Optional<Integer> findNextStep(Long workflowId, Integer currentStep) {
        return detailRepository.findByWorkflowRequestsIdOrderByStepNumberAscIdAsc(workflowId).stream()
            .map(WorkflowRequestDetail::getStepNumber)
            .filter(n -> n > currentStep)
            .min(Integer::compareTo);
    }
}
