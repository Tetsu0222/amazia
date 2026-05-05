package com.example.workflow.dto;

import com.example.workflow.entity.WorkflowRequest;
import com.example.workflow.entity.WorkflowRequestDetail;

import java.time.LocalDateTime;
import java.util.List;

public class WorkflowResponse {

    private Long id;
    private String targetType;
    private Long targetId;
    private Long requestedBy;
    private String status;
    private String payload;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<WorkflowDetailResponse> details;

    public static WorkflowResponse from(WorkflowRequest req, List<WorkflowRequestDetail> details) {
        WorkflowResponse r = new WorkflowResponse();
        r.id           = req.getId();
        r.targetType   = req.getTargetType();
        r.targetId     = req.getTargetId();
        r.requestedBy  = req.getRequestedBy();
        r.status       = req.getStatus();
        r.payload      = req.getPayload();
        r.completedAt  = req.getCompletedAt();
        r.createdAt    = req.getCreatedAt();
        r.updatedAt    = req.getUpdatedAt();
        r.details      = details.stream().map(WorkflowDetailResponse::from).toList();
        return r;
    }

    public Long getId() { return id; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public Long getRequestedBy() { return requestedBy; }
    public String getStatus() { return status; }
    public String getPayload() { return payload; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public List<WorkflowDetailResponse> getDetails() { return details; }
}
