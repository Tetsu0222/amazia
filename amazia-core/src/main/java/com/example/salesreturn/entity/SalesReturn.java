package com.example.salesreturn.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sales_return")
public class SalesReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sales_id", nullable = false)
    private Long salesId;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "approver_id")
    private Long approverId;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "notified_user", nullable = false)
    private boolean notifiedUser = false;

    @Column(name = "notified_admin", nullable = false)
    private boolean notifiedAdmin = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getSalesId() { return salesId; }
    public void setSalesId(Long salesId) { this.salesId = salesId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Long getApproverId() { return approverId; }
    public void setApproverId(Long approverId) { this.approverId = approverId; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public boolean isNotifiedUser() { return notifiedUser; }
    public void setNotifiedUser(boolean notifiedUser) { this.notifiedUser = notifiedUser; }
    public boolean isNotifiedAdmin() { return notifiedAdmin; }
    public void setNotifiedAdmin(boolean notifiedAdmin) { this.notifiedAdmin = notifiedAdmin; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
