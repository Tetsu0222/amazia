package com.example.inquiry.service;

import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 問い合わせ対象ラベル解決（フェーズ18 / 設計書 §6.1）。
 *
 * <p>{@code target_type} ごとに config の `target-labels` テンプレートを展開する。
 * `product` の場合のみ DB を引いて商品名を埋める。Service 層・通知 dispatcher の双方から呼ばれる。
 */
@Component
public class InquiryTargetLabelResolver {

    private final ProductRepository productRepository;

    @Value("${amazia.inquiry.target-labels.delivery}")
    private String deliveryLabelTpl;

    @Value("${amazia.inquiry.target-labels.product}")
    private String productLabelTpl;

    @Value("${amazia.inquiry.target-labels.sales}")
    private String salesLabelTpl;

    @Value("${amazia.inquiry.target-labels.generic}")
    private String genericLabel;

    public InquiryTargetLabelResolver(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public String resolve(String targetType, Long targetId) {
        if (targetType == null) {
            return genericLabel;
        }
        return switch (targetType) {
            case "delivery" -> deliveryLabelTpl.replace("{target_id}", String.valueOf(targetId));
            case "sales"    -> salesLabelTpl.replace("{target_id}", String.valueOf(targetId));
            case "product"  -> resolveProductLabel(targetId);
            default -> genericLabel;
        };
    }

    private String resolveProductLabel(Long productId) {
        Optional<Product> p = productRepository.findById(productId);
        String productName = p.map(Product::getName).orElse("(削除済み商品)");
        return productLabelTpl
                .replace("{target_id}", String.valueOf(productId))
                .replace("{product_name}", productName);
    }
}
