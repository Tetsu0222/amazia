package com.example.product.service;

import com.example.product.dto.PreorderProductItem;
import com.example.product.entity.PreorderStatus;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import com.example.sku.entity.ProductSku;
import com.example.sku.repository.ProductSkuRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Console: 予約管理画面向け 予約商品一覧取得 Service。
 *
 * 設計書 phase16_ui_ux_improvement.md §2-4-3 / §2-4-4。
 * PreorderStatusService が PRE_ORDER と判定した商品のみ抽出し、
 * sales.is_preorder = TRUE のレコードを商品単位で集計（数量・金額）して返す。
 *
 * メモリ配慮: 商品ループ内で sales を個別 SELECT せず、対象 SKU 集合で 1 回の SELECT
 * に集約してメモリ上で集計する（test_insights カテゴリ7-2 / phase15 と同方針）。
 */
@Service
public class ListPreorderProductsService {

    private final ProductRepository productRepository;
    private final ProductSkuRepository skuRepository;
    private final SalesRepository salesRepository;
    private final PreorderStatusService preorderStatusService;
    private final Clock clock;

    public ListPreorderProductsService(ProductRepository productRepository,
                                       ProductSkuRepository skuRepository,
                                       SalesRepository salesRepository,
                                       PreorderStatusService preorderStatusService,
                                       Clock clock) {
        this.productRepository = productRepository;
        this.skuRepository = skuRepository;
        this.salesRepository = salesRepository;
        this.preorderStatusService = preorderStatusService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PreorderProductItem> list() {
        List<Product> preorderProducts = productRepository.findAll().stream()
                .filter(p -> preorderStatusService.judge(p.getId()) == PreorderStatus.PRE_ORDER)
                .toList();
        if (preorderProducts.isEmpty()) return List.of();

        List<Long> productIds = preorderProducts.stream().map(Product::getId).toList();
        List<ProductSku> skus = skuRepository.findByProductIdIn(productIds);

        // skuId -> productId
        Map<Long, Long> skuToProduct = skus.stream()
                .collect(Collectors.toMap(ProductSku::getId, ProductSku::getProductId));
        Set<Long> skuIds = skuToProduct.keySet();

        // 予約 sales を 1 回でまとめて取得
        Map<Long, long[]> aggregateByProduct = new HashMap<>();
        if (!skuIds.isEmpty()) {
            List<Sales> preorderSales = salesRepository.findByIsPreorderTrueAndSkuIdIn(skuIds);
            for (Sales s : preorderSales) {
                Long pid = skuToProduct.get(s.getSkuId());
                if (pid == null) continue;
                long[] cur = aggregateByProduct.computeIfAbsent(pid, k -> new long[]{0L, 0L});
                cur[0] += s.getQuantity();
                cur[1] += s.getAmount();
            }
        }

        LocalDate today = LocalDate.now(clock);
        List<PreorderProductItem> result = new ArrayList<>(preorderProducts.size());
        for (Product p : preorderProducts) {
            long[] agg = aggregateByProduct.getOrDefault(p.getId(), new long[]{0L, 0L});
            Long days = p.getReleaseDate() != null
                    ? ChronoUnit.DAYS.between(today, p.getReleaseDate())
                    : null;
            result.add(new PreorderProductItem(
                    p.getId(),
                    p.getName(),
                    p.getPreorderStartDate(),
                    p.getReleaseDate(),
                    days,
                    p.isAcceptPreorder(),
                    p.isActive(),
                    agg[0],
                    agg[1]
            ));
        }
        result.sort(Comparator.comparing(
                PreorderProductItem::getReleaseDate,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }
}
