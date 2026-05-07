package com.example.product.service;

import com.example.product.dto.ProductAdminSummary;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuPrice;
import com.example.sku.entity.ProductSkuStock;
import com.example.sku.repository.ProductSkuPriceRepository;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminListProductService {

    private final ProductRepository productRepository;
    private final ProductSkuRepository skuRepository;
    private final ProductSkuPriceRepository priceRepository;
    private final ProductSkuStockRepository stockRepository;

    public AdminListProductService(ProductRepository productRepository,
                                   ProductSkuRepository skuRepository,
                                   ProductSkuPriceRepository priceRepository,
                                   ProductSkuStockRepository stockRepository) {
        this.productRepository = productRepository;
        this.skuRepository = skuRepository;
        this.priceRepository = priceRepository;
        this.stockRepository = stockRepository;
    }

    public List<ProductAdminSummary> getAll() {
        List<Product> products = productRepository.findAll();
        List<Long> productIds = products.stream().map(Product::getId).collect(Collectors.toList());

        List<ProductSku> allSkus = skuRepository.findByProductIdIn(productIds);
        List<Long> skuIds = allSkus.stream().map(ProductSku::getId).collect(Collectors.toList());

        Map<Long, List<ProductSku>> skusByProduct = allSkus.stream()
                .collect(Collectors.groupingBy(ProductSku::getProductId));

        Map<Long, Integer> priceBySkuId = skuIds.isEmpty() ? Map.of() :
                priceRepository.findBySkuIdIn(skuIds).stream()
                        .collect(Collectors.toMap(ProductSkuPrice::getSkuId, ProductSkuPrice::getPrice));

        Map<Long, Integer> stockBySkuId = skuIds.isEmpty() ? Map.of() :
                stockRepository.findBySkuIdIn(skuIds).stream()
                        .collect(Collectors.toMap(ProductSkuStock::getSkuId, ProductSkuStock::getQuantity));

        return products.stream().map(p -> {
            ProductAdminSummary s = new ProductAdminSummary();
            s.setId(p.getId());
            s.setName(p.getName());
            s.setStatusCode(p.getStatusCode());
            s.setPublishStart(p.getPublishStart());
            s.setPublishEnd(p.getPublishEnd());
            s.setActive(p.isActive());

            List<ProductSku> skus = skusByProduct.getOrDefault(p.getId(), List.of());
            s.setSkuCount(skus.size());

            List<Integer> prices = skus.stream()
                    .map(sku -> priceBySkuId.get(sku.getId()))
                    .filter(price -> price != null)
                    .collect(Collectors.toList());
            s.setMinPrice(prices.stream().mapToInt(Integer::intValue).min().isPresent()
                    ? prices.stream().mapToInt(Integer::intValue).min().getAsInt() : null);
            s.setMaxPrice(prices.stream().mapToInt(Integer::intValue).max().isPresent()
                    ? prices.stream().mapToInt(Integer::intValue).max().getAsInt() : null);

            int total = skus.stream()
                    .mapToInt(sku -> stockBySkuId.getOrDefault(sku.getId(), 0))
                    .sum();
            s.setTotalStock(total);

            return s;
        }).collect(Collectors.toList());
    }
}
