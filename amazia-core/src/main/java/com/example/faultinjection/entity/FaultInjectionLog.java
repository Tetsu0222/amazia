package com.example.faultinjection.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.Check;
import java.time.LocalDateTime;

@Entity
@Table(name = "fault_injection_logs")
@Check(constraints = "environment IN ('dev', 'staging')")
public class FaultInjectionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "injector_name", nullable = false, length = 100)
    private String injectorName;

    @NotNull
    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;

    @NotNull
    @Column(name = "triggered_by", nullable = false, length = 50)
    private String triggeredBy;

    @NotNull
    @Column(nullable = false, length = 20)
    private String environment;

    @Column(name = "target_summary", columnDefinition = "TEXT")
    private String targetSummary;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (triggeredAt == null) triggeredAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getInjectorName() { return injectorName; }
    public void setInjectorName(String injectorName) { this.injectorName = injectorName; }
    public LocalDateTime getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(LocalDateTime triggeredAt) { this.triggeredAt = triggeredAt; }
    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getTargetSummary() { return targetSummary; }
    public void setTargetSummary(String targetSummary) { this.targetSummary = targetSummary; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
