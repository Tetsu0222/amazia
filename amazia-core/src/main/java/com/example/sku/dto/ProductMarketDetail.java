package com.example.sku.dto;

import com.example.product.entity.PreorderStatus;
import com.example.product.entity.Product;
import java.util.List;

public class ProductMarketDetail {
    private Product product;
    private List<SkuDetail> skus;
    private PreorderStatus preorderStatus;

    public ProductMarketDetail(Product product, List<SkuDetail> skus, PreorderStatus preorderStatus) {
        this.product = product;
        this.skus = skus;
        this.preorderStatus = preorderStatus;
    }

    public Product getProduct() { return product; }
    public List<SkuDetail> getSkus() { return skus; }
    public PreorderStatus getPreorderStatus() { return preorderStatus; }
}
