package com.example.scheduledprice.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.Check;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_sku_scheduled_prices")
@Check(constraints = "scheduled_price >= 0")
public class ProductSkuScheduledPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @NotNull
    @Column(name = "scheduled_price", nullable = false)
    private Integer scheduledPrice;

    @NotNull
    @Column(name = "apply_date", nullable = false)
    private LocalDate applyDate;

    @NotNull
    @Column(name = "is_pending", nullable = false)
    private Boolean isPending = Boolean.TRUE;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isPending == null) isPending = Boolean.TRUE;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public Integer getScheduledPrice() { return scheduledPrice; }
    public void setScheduledPrice(Integer scheduledPrice) { this.scheduledPrice = scheduledPrice; }
    public LocalDate getApplyDate() { return applyDate; }
    public void setApplyDate(LocalDate applyDate) { this.applyDate = applyDate; }
    public Boolean getIsPending() { return isPending; }
    public void setIsPending(Boolean isPending) { this.isPending = isPending; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
