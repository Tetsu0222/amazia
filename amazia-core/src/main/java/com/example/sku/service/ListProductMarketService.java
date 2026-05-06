package com.example.sku.service;

import com.example.product.entity.PreorderStatus;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.product.service.PreorderStatusService;
import com.example.sku.dto.ProductMarketDetail;
import com.example.sku.dto.ProductMarketSummary;
import com.example.sku.dto.SkuDetail;
import com.example.sku.entity.ProductSku;
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
    private final PreorderStatusService preorderStatusService;

    public ListProductMarketService(ProductRepository productRepository,
                                    ProductSkuRepository skuRepository,
                                    ProductSkuPriceRepository priceRepository,
                                    ProductSkuStockRepository stockRepository,
                                    ProductSkuImageRepository imageRepository,
                                    PreorderStatusService preorderStatusService) {
        this.productRepository = productRepository;
        this.skuRepository = skuRepository;
        this.priceRepository = priceRepository;
        this.stockRepository = stockRepository;
        this.imageRepository = imageRepository;
        this.preorderStatusService = preorderStatusService;
    }

    public List<ProductMarketSummary> listMarket() {
        // 設計書 phase14_5_preorder_status.md §4-2:
        //   NOT_PUBLIC のみ一覧から除外。SOLD_OUT / BACK_ORDER / PRE_ORDER 等は表示する
        //   （ステータス別の見せ方は Market 側で分岐）
        return productRepository.findAll().stream()
                .map(this::buildSummary)
                .filter(summary -> summary != null)
                .filter(summary -> summary.getPreorderStatus() != PreorderStatus.NOT_PUBLIC)
                .collect(Collectors.toList());
    }

    public ProductMarketDetail getMarketDetail(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品が見つかりません"));

        List<SkuDetail> skuDetails = skuRepository.findByProductIdOrderByIdAsc(productId).stream()
                .map(this::buildSkuDetail)
                .collect(Collectors.toList());

        PreorderStatus status = preorderStatusService.judge(productId);
        return new ProductMarketDetail(product, skuDetails, status);
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

        // price 未登録 SKU が混入してもプレースホルダで集計が壊れないよう、登録済 SKU のみで min を取る
        Integer minPrice = skus.stream()
                .map(sku -> priceRepository.findBySkuId(sku.getId()).map(p -> p.getPrice()).orElse(null))
                .filter(p -> p != null)
                .min(Integer::compareTo)
                .orElse(null);

        String mainImage = skus.stream()
                .flatMap(sku -> imageRepository.findBySkuIdOrderBySortOrderAsc(sku.getId()).stream()
                        .map(img -> "/api/skus/" + sku.getId() + "/image-file/" + extractFilename(img.getImagePath())))
                .findFirst()
                .orElse(null);

        PreorderStatus status = preorderStatusService.judge(product.getId());

        return new ProductMarketSummary(
                product.getId(), product.getName(), product.getDescription(),
                minPrice, totalStock, mainImage,
                status,
                product.getReleaseDate(), product.getPreorderStartDate(),
                product.isAcceptPreorder(), product.isAcceptBackorder());
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
