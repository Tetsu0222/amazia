package com.example.warehouse.entity;

import jakarta.persistence.*;

/**
 * 倉庫マスタ（フェーズ15 r5 / RRR-3）。
 * schema.sql の INSERT IGNORE で id=1 'default' を投入（並行運用ダミー倉庫）。
 */
@Entity
@Table(name = "warehouses")
public class Warehouse {

    @Id
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
