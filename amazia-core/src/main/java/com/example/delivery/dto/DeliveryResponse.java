package com.example.delivery.dto;

import com.example.delivery.entity.Delivery;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 配送実体のレスポンス DTO（GET / List 共通）。
 */
public class DeliveryResponse {

    private final Long id;
    private final Long salesId;
    private final Long shippingAddressId;
    private final Long shippingMethodId;
    private final Long shippingStatusId;
    private final String trackingCode;
    private final LocalDate scheduledDate;
    private final LocalDate shippedDate;
    private final LocalDate deliveredDate;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public DeliveryResponse(Delivery d) {
        this.id = d.getId();
        this.salesId = d.getSalesId();
        this.shippingAddressId = d.getShippingAddressId();
        this.shippingMethodId = d.getShippingMethodId();
        this.shippingStatusId = d.getShippingStatusId();
        this.trackingCode = d.getTrackingCode();
        this.scheduledDate = d.getScheduledDate();
        this.shippedDate = d.getShippedDate();
        this.deliveredDate = d.getDeliveredDate();
        this.createdAt = d.getCreatedAt();
        this.updatedAt = d.getUpdatedAt();
    }

    public Long getId() { return id; }
    public Long getSalesId() { return salesId; }
    public Long getShippingAddressId() { return shippingAddressId; }
    public Long getShippingMethodId() { return shippingMethodId; }
    public Long getShippingStatusId() { return shippingStatusId; }
    public String getTrackingCode() { return trackingCode; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public LocalDate getShippedDate() { return shippedDate; }
    public LocalDate getDeliveredDate() { return deliveredDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
