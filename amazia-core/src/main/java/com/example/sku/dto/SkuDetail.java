package com.example.sku.dto;

import java.util.List;

public class SkuDetail {
    private Long skuId;
    private String skuCode;
    private String color;
    private String size;
    private String status;
    private Integer price;
    private Integer stock;
    private List<String> images;

    public SkuDetail(Long skuId, String skuCode, String color, String size, String status,
                     Integer price, Integer stock, List<String> images) {
        this.skuId = skuId;
        this.skuCode = skuCode;
        this.color = color;
        this.size = size;
        this.status = status;
        this.price = price;
        this.stock = stock;
        this.images = images;
    }

    public Long getSkuId() { return skuId; }
    public String getSkuCode() { return skuCode; }
    public String getColor() { return color; }
    public String getSize() { return size; }
    public String getStatus() { return status; }
    public Integer getPrice() { return price; }
    public Integer getStock() { return stock; }
    public List<String> getImages() { return images; }
}
