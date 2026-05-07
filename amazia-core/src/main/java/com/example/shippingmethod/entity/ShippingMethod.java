package com.example.shippingmethod.entity;

import jakarta.persistence.*;

/**
 * 配送方法マスタ（フェーズ15 r5 / P5-1）。
 * schema.sql の INSERT IGNORE で home_delivery / konbini_pickup / dropoff を投入。
 */
@Entity
@Table(name = "shipping_methods")
public class ShippingMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(length = 255)
    private String description;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
