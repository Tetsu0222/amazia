package com.example.sku.service;

import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.sku.dto.ProductMarketDetail;
import com.example.sku.dto.ProductMarketSummary;
import com.example.sku.dto.SkuDetail;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuImage;
import com.example.sku.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ListProductMarketService {

    private final ProductRepository productRepository;
    private final ProductSkuRepository skuRepository;
    private final ProductSkuPriceRepository priceRepository;
    private final ProductSkuStockRepository stockRepository;
    private final ProductSkuImageRepository imageRepository;

    public ListProductMarketService(ProductRepository productRepository,
                                    ProductSkuRepository skuRepository,
                                    ProductSkuPriceRepository priceRepository,
                                    ProductSkuStockRepository stockRepository,
                                    ProductSkuImageRepository imageRepository) {
        this.productRepository = productRepository;
        this.skuRepository = skuRepository;
        this.priceRepository = priceRepository;
        this.stockRepository = stockRepository;
        this.imageRepository = imageRepository;
    }

    public List<ProductMarketSummary> listMarket() {
        return productRepository.findAll().stream()
                .filter(Product::isPublished)
                .map(product -> buildSummary(product))
                .filter(summary -> summary != null)
                .collect(Collectors.toList());
    }

    public ProductMarketDetail getMarketDetail(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品が見つかりません"));

        List<SkuDetail> skuDetails = skuRepository.findByProductIdOrderByIdAsc(productId).stream()
                .map(sku -> buildSkuDetail(sku))
                .collect(Collectors.toList());

        return new ProductMarketDetail(product, skuDetails);
    }

    private String extractFilename(String imagePath) {
        int lastSlash = imagePath.lastIndexOf('/');
        return lastSlash >= 0 ? imagePath.substring(lastSlash + 1) : imagePath;
    }

    private ProductMarketSummary buildSummary(Product product) {
        List<ProductSku> skus = skuRepository.findByProductId(product.getId());
        if (skus.isEmpty()) return null;

        int totalStock = skus.stream()
                .mapToInt(sku -> stockRepository.findBySkuId(sku.getId())
                        .map(s -> s.getQuantity()).orElse(0))
                .sum();
        if (totalStock == 0) return null;

        int minPrice = skus.stream()
                .mapToInt(sku -> priceRepository.findBySkuId(sku.getId())
                        .map(p -> p.getPrice()).orElse(Integer.MAX_VALUE))
                .min().orElse(0);

        String mainImage = skus.stream()
                .flatMap(sku -> imageRepository.findBySkuIdOrderBySortOrderAsc(sku.getId()).stream()
                        .map(img -> "/api/skus/" + sku.getId() + "/image-file/" + extractFilename(img.getImagePath())))
                .findFirst()
                .orElse(null);

        return new ProductMarketSummary(product.getId(), product.getName(), product.getDescription(),
                minPrice, totalStock, mainImage);
    }

    private SkuDetail buildSkuDetail(ProductSku sku) {
        Integer price = priceRepository.findBySkuId(sku.getId())
                .map(p -> p.getPrice()).orElse(null);
        Integer stock = stockRepository.findBySkuId(sku.getId())
                .map(s -> s.getQuantity()).orElse(0);
        List<String> images = imageRepository.findBySkuIdOrderBySortOrderAsc(sku.getId()).stream()
                .map(img -> "/api/skus/" + sku.getId() + "/image-file/" + extractFilename(img.getImagePath()))
                .collect(Collectors.toList());

        return new SkuDetail(sku.getId(), sku.getSkuCode(), sku.getColor(), sku.getSize(),
                sku.getStatus(), price, stock, images);
    }
}
