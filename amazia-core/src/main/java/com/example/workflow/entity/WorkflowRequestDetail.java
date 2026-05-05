package com.example.workflow.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_requests_detail")
public class WorkflowRequestDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_requests_id", nullable = false)
    private Long workflowRequestsId;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(name = "target_role", nullable = false)
    private String targetRole;

    @Column(name = "destination_user_id")
    private Long destinationUserId;

    @Column(name = "destination_name")
    private String destinationName;

    @Column(name = "approver_user_id")
    private Long approverUserId;

    @Column(name = "approver_name")
    private String approverName;

    @Column(nullable = false)
    private String status;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getWorkflowRequestsId() { return workflowRequestsId; }
    public void setWorkflowRequestsId(Long workflowRequestsId) { this.workflowRequestsId = workflowRequestsId; }
    public Integer getStepNumber() { return stepNumber; }
    public void setStepNumber(Integer stepNumber) { this.stepNumber = stepNumber; }
    public String getTargetRole() { return targetRole; }
    public void setTargetRole(String targetRole) { this.targetRole = targetRole; }
    public Long getDestinationUserId() { return destinationUserId; }
    public void setDestinationUserId(Long destinationUserId) { this.destinationUserId = destinationUserId; }
    public String getDestinationName() { return destinationName; }
    public void setDestinationName(String destinationName) { this.destinationName = destinationName; }
    public Long getApproverUserId() { return approverUserId; }
    public void setApproverUserId(Long approverUserId) { this.approverUserId = approverUserId; }
    public String getApproverName() { return approverName; }
    public void setApproverName(String approverName) { this.approverName = approverName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
