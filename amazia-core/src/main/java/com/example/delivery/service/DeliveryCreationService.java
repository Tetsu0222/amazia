package com.example.delivery.service;

import com.example.delivery.entity.Delivery;
import com.example.delivery.repository.DeliveryRepository;
import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuStock;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

/**
 * 配送実体（{@code deliveries}）の生成 Service（フェーズ15 r5 / RR-3 / RR-4 / RRRR-3）。
 *
 * <p>{@code OrderConfirmationService.confirm()} の {@code @Transactional} 配下から呼び出され、
 * 注文確定と同時に {@code deliveries} を {@code PENDING} で 1 件生成する。
 *
 * <p>過渡期シグネチャ：phase14 r2 で {@code sales.shipping_method_id} カラムが追加されるまでは
 * {@code (salesId, shippingMethodId)} の二引数で受け取る。phase14 r2 完了後は単引数 {@code (salesId)} に移行。
 */
@Service
public class DeliveryCreationService {

    private final SalesRepository salesRepository;
    private final ProductSkuRepository skuRepository;
    private final ProductSkuStockRepository skuStockRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryScheduleService deliveryScheduleService;

    private final long pendingStatusId;

    public DeliveryCreationService(
            SalesRepository salesRepository,
            ProductSkuRepository skuRepository,
            ProductSkuStockRepository skuStockRepository,
            DeliveryRepository deliveryRepository,
            DeliveryScheduleService deliveryScheduleService,
            @Value("${amazia.sales.shipping-statuses.pending-id}") long pendingStatusId) {
        this.salesRepository = salesRepository;
        this.skuRepository = skuRepository;
        this.skuStockRepository = skuStockRepository;
        this.deliveryRepository = deliveryRepository;
        this.deliveryScheduleService = deliveryScheduleService;
        this.pendingStatusId = pendingStatusId;
    }

    /**
     * 注文確定 Service から呼び出され、{@code deliveries} を生成する。
     *
     * @param salesId          作成済み {@code sales.id}
     * @param shippingMethodId 配送方法 ID（phase14 r2 完了後は不要 / RRRR-3）
     * @return 生成された {@link Delivery}
     */
    @Transactional
    public Delivery createForSales(Long salesId, Long shippingMethodId) {
        Sales sales = salesRepository.findById(salesId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "sales not found"));

        // 防御的バリデーション（RRR-2）：通常購入で在庫切れを観測した場合は拒否。
        // 通常は OrderConfirmationService 側で減算済みのため到達しないが、最後の砦として保持する。
        ProductSku sku = skuRepository.findById(sales.getSkuId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "sku not found"));
        ProductSkuStock stock = skuStockRepository.findBySkuId(sku.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "sku stock not registered"));

        if (!sales.isPreorder() && stock.getQuantity() < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "out of stock at delivery creation");
        }

        // 配送予定日：通常購入は stock を入力として算出、予約購入は null（入荷時に再計算 / RRR-4）
        LocalDate scheduledDate = sales.isPreorder()
                ? null
                : deliveryScheduleService.calculate(sales, stock.getQuantity() + sales.getQuantity());
        // ↑ stock.getQuantity() は OrderConfirmationService で既に減算後の値のため、注文時点の在庫に戻して計算

        Delivery delivery = new Delivery();
        delivery.setSalesId(salesId);
        delivery.setShippingAddressId(sales.getShippingAddressId());
        delivery.setShippingMethodId(shippingMethodId);
        delivery.setShippingStatusId(pendingStatusId);
        delivery.setScheduledDate(scheduledDate);
        // tracking_code / shipped_date / delivered_date は NULL のまま

        return deliveryRepository.saveAndFlush(delivery);
    }
}
