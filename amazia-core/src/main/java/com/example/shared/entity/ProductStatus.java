package com.example.shared.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "product_statuses")
public class ProductStatus {

    @Id
    private String code;

    private String name;

    private Integer sortOrder;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
