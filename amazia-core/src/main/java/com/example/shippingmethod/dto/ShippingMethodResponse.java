package com.example.shippingmethod.dto;

import com.example.shippingmethod.entity.ShippingMethod;

/**
 * 配送方法マスタのレスポンス DTO。
 */
public class ShippingMethodResponse {

    private final Long id;
    private final String name;
    private final String description;

    public ShippingMethodResponse(ShippingMethod m) {
        this.id = m.getId();
        this.name = m.getName();
        this.description = m.getDescription();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
}
