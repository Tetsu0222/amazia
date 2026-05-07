package com.example.delivery.controller;

import com.example.delivery.dto.DeliveryResponse;
import com.example.delivery.dto.UpdateShippingStatusRequest;
import com.example.delivery.entity.Delivery;
import com.example.delivery.repository.DeliveryRepository;
import com.example.delivery.service.DeliveryStatusTransitionService;
import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import com.example.sku.entity.ProductSkuStock;
import com.example.sku.repository.ProductSkuStockRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 配送ステータス遷移 API（PATCH /api/deliveries/{id}/status）。
 *
 * <p>在庫不足（{@code 409 CONFLICT}）を捕捉し、
 * {@link DeliveryStatusTransitionService#recordShippingBlockedLog} を
 * {@code REQUIRES_NEW} で呼び出して PENDING 維持と {@code shipping_blocked_insufficient_stock}
 * 記録を両立させる（P5-4）。
 */
@RestController
@RequestMapping("/api")
public class UpdateShippingStatusController {

    private final DeliveryStatusTransitionService transitionService;
    private final DeliveryRepository deliveryRepository;
    private final SalesRepository salesRepository;
    private final ProductSkuStockRepository skuStockRepository;

    public UpdateShippingStatusController(DeliveryStatusTransitionService transitionService,
                                          DeliveryRepository deliveryRepository,
                                          SalesRepository salesRepository,
                                          ProductSkuStockRepository skuStockRepository) {
        this.transitionService = transitionService;
        this.deliveryRepository = deliveryRepository;
        this.salesRepository = salesRepository;
        this.skuStockRepository = skuStockRepository;
    }

    @PatchMapping("/deliveries/{id}/status")
    public ResponseEntity<DeliveryResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateShippingStatusRequest request,
                                                   @RequestHeader("X-User-Id") Long userId) {
        try {
            Delivery updated = transitionService.transition(id, request.getShippingStatusId(), userId);
            return ResponseEntity.ok(new DeliveryResponse(updated));
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() == HttpStatus.CONFLICT.value()
                    && ex.getReason() != null
                    && ex.getReason().contains("preorder shipment blocked")) {
                recordBlockedLogSafely(id, userId);
            }
            throw ex;
        }
    }

    /**
     * 在庫不足ログを別トランザクション（REQUIRES_NEW）で記録する。
     * 例外発生時のロールバックの影響を受けず PENDING 維持と log 記録を両立させる（P5-4）。
     */
    private void recordBlockedLogSafely(Long deliveryId, Long actorUserId) {
        try {
            // ロールバック後に再フェッチして sales / stock の不足量を計算
            Delivery d = deliveryRepository.findById(deliveryId).orElse(null);
            if (d == null) return;
            Sales sales = salesRepository.findById(d.getSalesId()).orElse(null);
            if (sales == null) return;
            ProductSkuStock stock = skuStockRepository.findBySkuId(sales.getSkuId()).orElse(null);
            int shortage = (stock == null) ? sales.getQuantity() : sales.getQuantity() - stock.getQuantity();
            transitionService.recordShippingBlockedLog(deliveryId, sales.getId(), shortage, actorUserId);
        } catch (Exception ignored) {
            // ログ記録の失敗は本来の 409 エラーレスポンスを邪魔しない
        }
    }
}
