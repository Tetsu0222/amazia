package com.example.delivery.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 配送実体（フェーズ15 r5 / RR-3 / R-1 / R-9）。
 *
 * <p>{@code sales : deliveries = 1 : 1}（{@code UNIQUE(sales_id)}）。
 * 配送ステータス遷移は {@code DeliveryStatusTransitionService} がアトミックに制御する。
 */
@Entity
@Table(
    name = "deliveries",
    uniqueConstraints = @UniqueConstraint(name = "uk_deliveries_sales_id", columnNames = "sales_id")
)
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sales_id", nullable = false, unique = true)
    private Long salesId;

    @Column(name = "shipping_address_id", nullable = false)
    private Long shippingAddressId;

    @Column(name = "shipping_method_id", nullable = false)
    private Long shippingMethodId;

    @Column(name = "shipping_status_id", nullable = false)
    private Long shippingStatusId;

    @Column(name = "tracking_code", length = 100)
    private String trackingCode;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "shipped_date")
    private LocalDate shippedDate;

    @Column(name = "delivered_date")
    private LocalDate deliveredDate;

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
    public Long getShippingAddressId() { return shippingAddressId; }
    public void setShippingAddressId(Long shippingAddressId) { this.shippingAddressId = shippingAddressId; }
    public Long getShippingMethodId() { return shippingMethodId; }
    public void setShippingMethodId(Long shippingMethodId) { this.shippingMethodId = shippingMethodId; }
    public Long getShippingStatusId() { return shippingStatusId; }
    public void setShippingStatusId(Long shippingStatusId) { this.shippingStatusId = shippingStatusId; }
    public String getTrackingCode() { return trackingCode; }
    public void setTrackingCode(String trackingCode) { this.trackingCode = trackingCode; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
    public LocalDate getShippedDate() { return shippedDate; }
    public void setShippedDate(LocalDate shippedDate) { this.shippedDate = shippedDate; }
    public LocalDate getDeliveredDate() { return deliveredDate; }
    public void setDeliveredDate(LocalDate deliveredDate) { this.deliveredDate = deliveredDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
