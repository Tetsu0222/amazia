package com.example.sku.dto;

import com.example.product.entity.Product;
import java.util.List;

public class ProductMarketDetail {
    private Product product;
    private List<SkuDetail> skus;

    public ProductMarketDetail(Product product, List<SkuDetail> skus) {
        this.product = product;
        this.skus = skus;
    }

    public Product getProduct() { return product; }
    public List<SkuDetail> getSkus() { return skus; }
}
