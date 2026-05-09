package com.example.inquiry.validator;

import com.example.delivery.entity.Delivery;
import com.example.delivery.repository.DeliveryRepository;
import com.example.inquiry.exception.ForbiddenInquiryAccessException;
import com.example.inquiry.exception.InquiryNotFoundException;
import com.example.inquiry.exception.InquiryValidationException;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 問い合わせ対象（{@code target_type} / {@code target_id}）の所有者検証（フェーズ18 / RV-5 / 設計書 §14）。
 *
 * <p>{@code target_type} ごとの所有者検証ロジックを 1 ファイルに集約する。
 * 検証成功時は何も返さず、失敗時はドメイン例外を送出する。
 */
@Component
public class InquiryTargetOwnershipValidator {

    private final DeliveryRepository deliveryRepository;
    private final SalesRepository salesRepository;
    private final ProductRepository productRepository;

    public InquiryTargetOwnershipValidator(DeliveryRepository deliveryRepository,
                                           SalesRepository salesRepository,
                                           ProductRepository productRepository) {
        this.deliveryRepository = deliveryRepository;
        this.salesRepository = salesRepository;
        this.productRepository = productRepository;
    }

    /**
     * @param targetType         delivery / product / sales / null（汎用）
     * @param targetId           対象 ID（targetType=null のときは null）
     * @param marketCustomerId   現在の Market 顧客 ID
     */
    public void validate(String targetType, Long targetId, Long marketCustomerId) {
        if (targetType == null) {
            if (targetId != null) {
                throw new InquiryValidationException(
                        "target_id must be null when target_type is null");
            }
            return;
        }
        if (targetId == null) {
            throw new InquiryValidationException(
                    "target_id is required when target_type is set");
        }

        switch (targetType) {
            case "delivery" -> validateDelivery(targetId, marketCustomerId);
            case "sales"    -> validateSales(targetId, marketCustomerId);
            case "product"  -> validateProduct(targetId);
            default -> throw new InquiryValidationException(
                    "unknown target_type: " + targetType);
        }
    }

    private void validateDelivery(Long deliveryId, Long marketCustomerId) {
        Delivery d = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new InquiryNotFoundException(
                        "delivery not found: " + deliveryId));
        // deliveries → sales.user_id で所有者検証（phase15 RR-7）
        Sales s = salesRepository.findById(d.getSalesId())
                .orElseThrow(() -> new InquiryNotFoundException(
                        "sales not found for delivery: " + deliveryId));
        if (!Objects.equals(s.getUserId(), marketCustomerId)) {
            throw new ForbiddenInquiryAccessException(
                    "delivery does not belong to current customer");
        }
    }

    private void validateSales(Long salesId, Long marketCustomerId) {
        Sales s = salesRepository.findById(salesId)
                .orElseThrow(() -> new InquiryNotFoundException(
                        "sales not found: " + salesId));
        if (!Objects.equals(s.getUserId(), marketCustomerId)) {
            throw new ForbiddenInquiryAccessException(
                    "sales does not belong to current customer");
        }
    }

    private void validateProduct(Long productId) {
        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new InquiryNotFoundException(
                        "product not found: " + productId));
        if (!p.isActive()) {
            // 非アクティブ商品は問い合わせ対象に出来ない（一覧 UI からも除外される想定）
            throw new InquiryValidationException(
                    "product is not active: " + productId);
        }
    }
}
