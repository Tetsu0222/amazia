package com.example.workflow.dto;

import com.example.workflow.entity.WorkflowRequestDetail;

import java.time.LocalDateTime;

public class WorkflowDetailResponse {

    private Long id;
    private Integer stepNumber;
    private String targetRole;
    private Long destinationUserId;
    private String destinationName;
    private Long approverUserId;
    private String approverName;
    private String status;
    private LocalDateTime updatedAt;

    public static WorkflowDetailResponse from(WorkflowRequestDetail d) {
        WorkflowDetailResponse r = new WorkflowDetailResponse();
        r.id                 = d.getId();
        r.stepNumber         = d.getStepNumber();
        r.targetRole         = d.getTargetRole();
        r.destinationUserId  = d.getDestinationUserId();
        r.destinationName    = d.getDestinationName();
        r.approverUserId     = d.getApproverUserId();
        r.approverName       = d.getApproverName();
        r.status             = d.getStatus();
        r.updatedAt          = d.getUpdatedAt();
        return r;
    }

    public Long getId() { return id; }
    public Integer getStepNumber() { return stepNumber; }
    public String getTargetRole() { return targetRole; }
    public Long getDestinationUserId() { return destinationUserId; }
    public String getDestinationName() { return destinationName; }
    public Long getApproverUserId() { return approverUserId; }
    public String getApproverName() { return approverName; }
    public String getStatus() { return status; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
