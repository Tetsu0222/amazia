package com.example.order.service;

import com.example.order.dto.PurchaseHistoryItem;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import com.example.shippingstatus.entity.ShippingStatus;
import com.example.shippingstatus.repository.ShippingStatusRepository;
import com.example.sku.entity.ProductSku;
import com.example.sku.repository.ProductSkuRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Market: ログイン中の会員の購入履歴を取得する Service。
 *
 * 設計書 r4 / Amazia Market §購入履歴。
 * sales を user_id で取得し、product_skus / products / shipping_statuses を JOIN して
 * 表示用の {@link PurchaseHistoryItem} に整形する。
 *
 * 集計はせず、画面表示のための整形のみを行う（フィルタ・並び替えは Market 側で実装）。
 */
@Service
public class GetMyPurchaseHistoryService {

    private final SalesRepository salesRepository;
    private final ProductSkuRepository skuRepository;
    private final ProductRepository productRepository;
    private final ShippingStatusRepository shippingStatusRepository;

    public GetMyPurchaseHistoryService(SalesRepository salesRepository,
                                       ProductSkuRepository skuRepository,
                                       ProductRepository productRepository,
                                       ShippingStatusRepository shippingStatusRepository) {
        this.salesRepository = salesRepository;
        this.skuRepository = skuRepository;
        this.productRepository = productRepository;
        this.shippingStatusRepository = shippingStatusRepository;
    }

    @Transactional(readOnly = true)
    public List<PurchaseHistoryItem> list(Long customerId) {
        List<Sales> salesList = salesRepository.findByUserIdOrderBySalesDateDesc(customerId);
        if (salesList.isEmpty()) return List.of();

        // 一括取得して N+1 回避
        Set<Long> skuIds = salesList.stream().map(Sales::getSkuId).collect(Collectors.toSet());
        List<ProductSku> skus = skuRepository.findAllById(skuIds);
        Map<Long, ProductSku> skuMap = skus.stream()
                .collect(Collectors.toMap(ProductSku::getId, s -> s));

        Set<Long> productIds = skus.stream().map(ProductSku::getProductId).collect(Collectors.toSet());
        Map<Long, Product> productMap = new HashMap<>();
        productRepository.findAllById(productIds).forEach(p -> productMap.put(p.getId(), p));

        Set<Long> statusIds = salesList.stream().map(Sales::getShippingStatusId).collect(Collectors.toSet());
        Map<Long, ShippingStatus> statusMap = new HashMap<>();
        shippingStatusRepository.findAllById(statusIds).forEach(s -> statusMap.put(s.getId(), s));

        return salesList.stream().map(s -> {
            ProductSku sku = skuMap.get(s.getSkuId());
            Product product = sku != null ? productMap.get(sku.getProductId()) : null;
            ShippingStatus status = statusMap.get(s.getShippingStatusId());
            return new PurchaseHistoryItem(
                    s.getId(),
                    s.getSalesDate(),
                    s.getShippingDate(),
                    s.getSkuId(),
                    product != null ? product.getName() : null,
                    sku != null ? sku.getColor() : null,
                    sku != null ? sku.getSize() : null,
                    s.getQuantity(),
                    s.getAmount(),
                    status != null ? status.getCode() : null,
                    s.getShippingMethodId(),
                    s.getPaymentMethodId(),
                    s.isPreorder()
            );
        }).toList();
    }
}
