package com.example.delivery.service;

import com.example.delivery.entity.Delivery;
import com.example.delivery.repository.DeliveryRepository;
import com.example.inventory.entity.Inventories;
import com.example.inventory.repository.InventoriesRepository;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 入荷再計算 Service（フェーズ15 r5 / RRR-4 / RRRR-4）。
 *
 * <p>{@link com.example.inbound.service.RegisterInboundService} から呼び出され、
 * 在庫切れで {@code scheduled_date = NULL} だった {@code deliveries} を
 * {@code sales.created_at} 昇順 FIFO で再計算する。
 *
 * <p>設計書 §入荷再計算ロジック の擬似コード：
 * <pre>
 *   on inbound(product_id, quantity):
 *     inventories を FOR UPDATE で取得（RRRR-4）
 *     candidates = deliveries JOIN sales WHERE sku.product_id = product_id
 *                  AND scheduled_date IS NULL ORDER BY sales.created_at ASC
 *     available = inventories.quantity（最新値）
 *     for each d in candidates:
 *       if available >= d.sales.quantity:
 *         d.scheduled_date = DeliveryScheduleService.calculate(sales, available)
 *         operation_logs に [inbound_recalc] で記録
 *         available -= d.sales.quantity
 *       else:
 *         break
 * </pre>
 *
 * <p>ループ中は {@code inventories.quantity} を DB に書き戻さず、Service 内ローカル変数
 * {@code available} で消費トラッキング（実販売減算は SHIPPED 遷移時 / RRRR-4）。
 */
@Service
public class DeliveryRescheduleService {

    private static final String ACTION = "update_scheduled_date";
    private static final String TARGET_TYPE = "deliveries";
    private static final String SCREEN_NAME = "core.batch.inbound_recalc";
    // バッチ起点なので api_name は NULL（命名規約 §6）

    private final DeliveryRepository deliveryRepository;
    private final SalesRepository salesRepository;
    private final InventoriesRepository inventoriesRepository;
    private final OperationLogRepository operationLogRepository;
    private final DeliveryScheduleService deliveryScheduleService;

    private final long defaultWarehouseId;
    private final String inboundRecalcReasonPrefix;

    public DeliveryRescheduleService(
            DeliveryRepository deliveryRepository,
            SalesRepository salesRepository,
            InventoriesRepository inventoriesRepository,
            OperationLogRepository operationLogRepository,
            DeliveryScheduleService deliveryScheduleService,
            @Value("${amazia.delivery.default-warehouse-id}") long defaultWarehouseId,
            @Value("${amazia.delivery.scheduled-date-reasons.inbound-recalc}") String inboundRecalcReasonPrefix) {
        this.deliveryRepository = deliveryRepository;
        this.salesRepository = salesRepository;
        this.inventoriesRepository = inventoriesRepository;
        this.operationLogRepository = operationLogRepository;
        this.deliveryScheduleService = deliveryScheduleService;
        this.defaultWarehouseId = defaultWarehouseId;
        this.inboundRecalcReasonPrefix = inboundRecalcReasonPrefix;
    }

    /**
     * 対象商品の在庫切れ {@code deliveries} を FIFO で再計算する。
     *
     * @param productId   対象商品 ID
     * @param actorUserId 操作者 ID（{@code users.id} / 入荷登録時は同じ管理者）
     */
    @Transactional
    public void recalculateForProduct(Long productId, Long actorUserId) {
        // 1. inventories 最新値を悲観ロックで取得（RRRR-4 / RRR-8）
        Inventories inventories = inventoriesRepository
                .findByProductIdAndWarehouseIdForUpdate(productId, defaultWarehouseId)
                .orElseThrow(() -> new IllegalStateException(
                        "inventories row missing for productId=" + productId));

        int available = inventories.getQuantity();

        // 2. 在庫切れ deliveries を FIFO 取得
        List<Delivery> candidates = deliveryRepository
                .findUnscheduledByProductIdOrderByCreatedAtAsc(productId);

        // 3. 古い順に充足できるものから scheduled_date を埋める
        for (Delivery d : candidates) {
            Sales sales = salesRepository.findById(d.getSalesId())
                    .orElseThrow(() -> new IllegalStateException(
                            "sales row missing for deliveryId=" + d.getId()));
            int needed = sales.getQuantity();
            if (available < needed) {
                break;
            }

            LocalDate newDate = deliveryScheduleService.calculate(sales, available);
            if (newDate == null) {
                // calculate が null を返すのは在庫不足のときのみ。直前で available >= needed を確認しているため到達しないはず。
                break;
            }
            LocalDate oldDate = d.getScheduledDate();
            d.setScheduledDate(newDate);
            deliveryRepository.save(d);

            recordLog(actorUserId, d.getId(), oldDate, newDate);
            available -= needed;
        }

        // 4. inventories.quantity の DB 書き戻しは行わない（実販売減算は SHIPPED 時 / RRRR-4）
    }

    private void recordLog(Long actorUserId, Long deliveryId, LocalDate oldDate, LocalDate newDate) {
        OperationLog log = new OperationLog();
        log.setUserId(actorUserId);
        log.setAction(ACTION);
        log.setTargetType(TARGET_TYPE);
        log.setTargetId(deliveryId);
        log.setScreenName(SCREEN_NAME);
        log.setApiName(null); // バッチ起点
        log.setComment(inboundRecalcReasonPrefix
                + " 旧:" + (oldDate == null ? "NULL" : oldDate)
                + " → 新:" + newDate);
        operationLogRepository.save(log);
    }
}
