package com.example.delivery.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 都道府県別リードタイムマスタ（フェーズX-5）。
 *
 * <p>{@code shipping_methods} × 都道府県（{@code address.prefecture} と厳密一致）でリードタイム日数を保持する。
 * 該当行が無い／{@code prefecture} が NULL の場合は {@code application.properties} の
 * {@code amazia.delivery.lead-time-days.*} にフォールバックする（{@link com.example.delivery.service.DeliveryScheduleService}）。
 */
@Entity
@Table(
    name = "shipping_lead_times",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_shipping_lead_times_method_pref",
        columnNames = {"shipping_method_id", "prefecture"}
    )
)
public class ShippingLeadTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shipping_method_id", nullable = false)
    private Long shippingMethodId;

    @Column(nullable = false, length = 20)
    private String prefecture;

    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays;

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
    public Long getShippingMethodId() { return shippingMethodId; }
    public void setShippingMethodId(Long shippingMethodId) { this.shippingMethodId = shippingMethodId; }
    public String getPrefecture() { return prefecture; }
    public void setPrefecture(String prefecture) { this.prefecture = prefecture; }
    public int getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(int leadTimeDays) { this.leadTimeDays = leadTimeDays; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
