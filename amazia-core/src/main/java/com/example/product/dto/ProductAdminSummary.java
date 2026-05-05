package com.example.product.dto;

import java.time.LocalDateTime;

public class ProductAdminSummary {

    private Long id;
    private String name;
    private String statusCode;
    private LocalDateTime publishStart;
    private LocalDateTime publishEnd;

    private int skuCount;
    private Integer minPrice;
    private Integer maxPrice;
    private int totalStock;

    public ProductAdminSummary() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }

    public LocalDateTime getPublishStart() { return publishStart; }
    public void setPublishStart(LocalDateTime publishStart) { this.publishStart = publishStart; }

    public LocalDateTime getPublishEnd() { return publishEnd; }
    public void setPublishEnd(LocalDateTime publishEnd) { this.publishEnd = publishEnd; }

    public int getSkuCount() { return skuCount; }
    public void setSkuCount(int skuCount) { this.skuCount = skuCount; }

    public Integer getMinPrice() { return minPrice; }
    public void setMinPrice(Integer minPrice) { this.minPrice = minPrice; }

    public Integer getMaxPrice() { return maxPrice; }
    public void setMaxPrice(Integer maxPrice) { this.maxPrice = maxPrice; }

    public int getTotalStock() { return totalStock; }
    public void setTotalStock(int totalStock) { this.totalStock = totalStock; }
}
