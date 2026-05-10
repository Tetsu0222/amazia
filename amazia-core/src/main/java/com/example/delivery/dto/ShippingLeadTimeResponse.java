package com.example.delivery.dto;

import com.example.delivery.entity.ShippingLeadTime;

import java.time.LocalDateTime;

public class ShippingLeadTimeResponse {

    private final Long id;
    private final Long shippingMethodId;
    private final String prefecture;
    private final int leadTimeDays;
    private final LocalDateTime updatedAt;

    public ShippingLeadTimeResponse(ShippingLeadTime entity) {
        this.id = entity.getId();
        this.shippingMethodId = entity.getShippingMethodId();
        this.prefecture = entity.getPrefecture();
        this.leadTimeDays = entity.getLeadTimeDays();
        this.updatedAt = entity.getUpdatedAt();
    }

    public Long getId() { return id; }
    public Long getShippingMethodId() { return shippingMethodId; }
    public String getPrefecture() { return prefecture; }
    public int getLeadTimeDays() { return leadTimeDays; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
