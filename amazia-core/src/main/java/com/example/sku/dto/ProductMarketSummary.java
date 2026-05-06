package com.example.sku.dto;

import com.example.product.entity.PreorderStatus;

import java.time.LocalDate;

public class ProductMarketSummary {
    private Long productId;
    private String productName;
    private String description;
    private Integer minPrice;
    private Integer totalStock;
    private String mainImage;
    private PreorderStatus preorderStatus;
    private LocalDate releaseDate;
    private LocalDate preorderStartDate;
    private boolean acceptPreorder;
    private boolean acceptBackorder;

    public ProductMarketSummary(Long productId, String productName, String description,
                                Integer minPrice, Integer totalStock, String mainImage,
                                PreorderStatus preorderStatus,
                                LocalDate releaseDate, LocalDate preorderStartDate,
                                boolean acceptPreorder, boolean acceptBackorder) {
        this.productId = productId;
        this.productName = productName;
        this.description = description;
        this.minPrice = minPrice;
        this.totalStock = totalStock;
        this.mainImage = mainImage;
        this.preorderStatus = preorderStatus;
        this.releaseDate = releaseDate;
        this.preorderStartDate = preorderStartDate;
        this.acceptPreorder = acceptPreorder;
        this.acceptBackorder = acceptBackorder;
    }

    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getDescription() { return description; }
    public Integer getMinPrice() { return minPrice; }
    public Integer getTotalStock() { return totalStock; }
    public String getMainImage() { return mainImage; }
    public PreorderStatus getPreorderStatus() { return preorderStatus; }
    public LocalDate getReleaseDate() { return releaseDate; }
    public LocalDate getPreorderStartDate() { return preorderStartDate; }
    public boolean isAcceptPreorder() { return acceptPreorder; }
    public boolean isAcceptBackorder() { return acceptBackorder; }
}
