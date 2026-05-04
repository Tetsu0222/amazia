package com.example.sku.dto;

public class ProductMarketSummary {
    private Long productId;
    private String productName;
    private String description;
    private Integer minPrice;
    private Integer totalStock;
    private String mainImage;

    public ProductMarketSummary(Long productId, String productName, String description,
                                Integer minPrice, Integer totalStock, String mainImage) {
        this.productId = productId;
        this.productName = productName;
        this.description = description;
        this.minPrice = minPrice;
        this.totalStock = totalStock;
        this.mainImage = mainImage;
    }

    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getDescription() { return description; }
    public Integer getMinPrice() { return minPrice; }
    public Integer getTotalStock() { return totalStock; }
    public String getMainImage() { return mainImage; }
}
