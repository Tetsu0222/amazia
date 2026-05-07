package com.example.delivery.service;

import com.example.delivery.entity.Delivery;
import com.example.delivery.repository.DeliveryRepository;
import com.example.inventory.service.InventorySyncService;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import com.example.sku.entity.ProductSku;
import com.example.sku.entity.ProductSkuStock;
import com.example.sku.entity.ProductSkuStockTransaction;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.repository.ProductSkuStockRepository;
import com.example.sku.repository.ProductSkuStockTransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * 配送ステータス遷移 Service（フェーズ15 r5 / R-4 / R-7 / RR-1 / P5-3 / P5-4）。
 *
 * <p>遷移可否表（設計書 §配送ステータス遷移ルール）：
 * <pre>
 *   PENDING          → SHIPPED
 *   SHIPPED          → DELIVERED
 *   DELIVERED        → RETURN_REQUESTED
 *   RETURN_REQUESTED → RETURNED
 * </pre>
 * 巻き戻し・スコープ外ステータスへの遷移は {@code 400} で拒否する。
 *
 * <p>{@code PENDING → SHIPPED} 遷移時の在庫処理（P5-3 / P5-4）：
 * <ul>
 *   <li>{@code is_preorder == false}（通常購入）：在庫操作なし（注文確定時に減算済み）</li>
 *   <li>{@code is_preorder == true}（予約購入）：本遷移時に {@code product_sku_stocks.quantity} を減算
 *     （{@code @Version} 楽観ロック）。在庫不足時は {@code 409} を投げて PENDING のまま維持し、
 *     {@code operation_logs.action='shipping_blocked_insufficient_stock'} を記録</li>
 * </ul>
 *
 * <p>並行運用期は {@code inventories.quantity} の同期減算が必要（RRRR-2）。Step B-5 で
 * {@code InventorySyncService.applyDelta} を本 Service に DI して呼び出す。
 */
@Service
public class DeliveryStatusTransitionService {

    private static final String ACTION_UPDATE = "update_shipping_status";
    private static final String ACTION_BLOCKED = "shipping_blocked_insufficient_stock";
    private static final String TARGET_TYPE = "deliveries";
    private static final String SCREEN_NAME = "console.delivery.update_status";
    private static final String API_NAME = "PATCH /api/deliveries/:id/status";

    private final DeliveryRepository deliveryRepository;
    private final SalesRepository salesRepository;
    private final ProductSkuRepository skuRepository;
    private final ProductSkuStockRepository skuStockRepository;
    private final ProductSkuStockTransactionRepository skuStockTransactionRepository;
    private final InventorySyncService inventorySyncService;
    private final OperationLogRepository operationLogRepository;

    private final long pendingId;
    private final long shippedId;
    private final long deliveredId;
    private final long returnRequestedId;
    private final long returnedId;
    private final long defaultWarehouseId;
    private final String txTypePreorderShipment;

    /** key=現在のステータス ID, value=許容される次ステータス ID 集合 */
    private final Map<Long, Set<Long>> allowedTransitions;

    public DeliveryStatusTransitionService(
            DeliveryRepository deliveryRepository,
            SalesRepository salesRepository,
            ProductSkuRepository skuRepository,
            ProductSkuStockRepository skuStockRepository,
            ProductSkuStockTransactionRepository skuStockTransactionRepository,
            InventorySyncService inventorySyncService,
            OperationLogRepository operationLogRepository,
            @Value("${amazia.sales.shipping-statuses.pending-id}")           long pendingId,
            @Value("${amazia.sales.shipping-statuses.shipped-id}")           long shippedId,
            @Value("${amazia.sales.shipping-statuses.delivered-id}")         long deliveredId,
            @Value("${amazia.sales.shipping-statuses.return-requested-id}")  long returnRequestedId,
            @Value("${amazia.sales.shipping-statuses.returned-id}")          long returnedId,
            @Value("${amazia.delivery.default-warehouse-id}")                long defaultWarehouseId,
            @Value("${amazia.delivery.sku-stock-tx-types.sale-preorder-shipment}") String txTypePreorderShipment) {
        this.deliveryRepository = deliveryRepository;
        this.salesRepository = salesRepository;
        this.skuRepository = skuRepository;
        this.skuStockRepository = skuStockRepository;
        this.skuStockTransactionRepository = skuStockTransactionRepository;
        this.inventorySyncService = inventorySyncService;
        this.operationLogRepository = operationLogRepository;
        this.pendingId = pendingId;
        this.shippedId = shippedId;
        this.deliveredId = deliveredId;
        this.returnRequestedId = returnRequestedId;
        this.returnedId = returnedId;
        this.defaultWarehouseId = defaultWarehouseId;
        this.txTypePreorderShipment = txTypePreorderShipment;

        this.allowedTransitions = Map.of(
                pendingId,         Set.of(shippedId),
                shippedId,         Set.of(deliveredId),
                deliveredId,       Set.of(returnRequestedId),
                returnRequestedId, Set.of(returnedId),
                returnedId,        Set.of()
        );
    }

    /**
     * 配送ステータスを遷移させる。
     *
     * @param deliveryId   対象配送 ID
     * @param nextStatusId 遷移先ステータス ID
     * @param actorUserId  操作者（{@code users.id}。{@code operation_logs.user_id} に記録）
     */
    @Transactional
    public Delivery transition(Long deliveryId, Long nextStatusId, Long actorUserId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "delivery not found"));

        Long currentStatusId = delivery.getShippingStatusId();
        if (currentStatusId.equals(nextStatusId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no-op transition");
        }
        Set<Long> allowed = allowedTransitions.getOrDefault(currentStatusId, Set.of());
        if (!allowed.contains(nextStatusId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "illegal status transition: " + currentStatusId + " -> " + nextStatusId);
        }

        // PENDING → SHIPPED：予約購入のみ在庫減算（P5-3 / P5-4）
        if (currentStatusId.equals(pendingId) && nextStatusId.equals(shippedId)) {
            applyShipmentStockChange(delivery, actorUserId);
        }

        // 日付の自動充填
        if (nextStatusId.equals(shippedId)) {
            delivery.setShippedDate(LocalDate.now());
        } else if (nextStatusId.equals(deliveredId)) {
            delivery.setDeliveredDate(LocalDate.now());
        }

        delivery.setShippingStatusId(nextStatusId);
        Delivery saved = deliveryRepository.saveAndFlush(delivery);

        recordLog(actorUserId, deliveryId, ACTION_UPDATE,
                "旧: " + currentStatusId + " → 新: " + nextStatusId);
        return saved;
    }

    /**
     * 出荷時の在庫減算（予約購入のみ）。在庫不足は 409 を投げて PENDING のまま維持する（P5-4）。
     */
    private void applyShipmentStockChange(Delivery delivery, Long actorUserId) {
        Sales sales = salesRepository.findById(delivery.getSalesId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "sales not found"));

        if (!sales.isPreorder()) {
            // 通常購入は注文確定時に減算済み（phase14 r4）
            return;
        }

        ProductSku sku = skuRepository.findById(sales.getSkuId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "sku not found"));
        ProductSkuStock stock = skuStockRepository.findBySkuId(sku.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "sku stock not registered"));

        if (stock.getQuantity() < sales.getQuantity()) {
            // 在庫不足：例外で全ロールバック（deliveries 状態は PENDING のまま維持）。
            // ただし operation_logs の記録は別 Service で行う必要があるため、
            // ここではロールバック前提で記録できないので、Controller 側で例外を捕捉して
            // 別トランザクションで記録する設計とする。
            // 暫定として recordBlockedLog をここで呼ぶと一緒にロールバックされる点に注意。
            // 本フェーズでは「例外 + PENDING 維持」を最優先し、ログは Step B-6 の
            // Controller 層で REQUIRES_NEW トランザクションで記録する方針とする。
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "preorder shipment blocked: insufficient stock");
        }

        // 在庫減算（@Version 楽観ロック）
        stock.setQuantity(stock.getQuantity() - sales.getQuantity());
        skuStockRepository.save(stock);

        // 在庫増減ログ
        ProductSkuStockTransaction tx = new ProductSkuStockTransaction();
        tx.setSkuId(sku.getId());
        tx.setType(txTypePreorderShipment);
        tx.setQuantity(-sales.getQuantity()); // 出荷は負数
        tx.setReferenceType("sales");
        tx.setReferenceId(sales.getId());
        tx.setCreatedByUserId(actorUserId);
        skuStockTransactionRepository.save(tx);

        // 並行運用：inventories も同期減算（RRRR-2）
        inventorySyncService.applyDelta(sku.getProductId(), defaultWarehouseId,
                -sales.getQuantity());
    }

    /**
     * 出荷時在庫不足の独立トランザクション記録（P5-4）。
     * Controller 層から例外捕捉後に呼び出すことで、PENDING 維持のロールバックと
     * operation_logs 記録を両立させる。
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordShippingBlockedLog(Long deliveryId, Long salesId, int shortage, Long actorUserId) {
        recordLog(actorUserId, deliveryId, ACTION_BLOCKED,
                "sales_id=" + salesId + ", shortage=" + shortage);
    }

    private void recordLog(Long actorUserId, Long targetId, String action, String comment) {
        OperationLog log = new OperationLog();
        log.setUserId(actorUserId);
        log.setAction(action);
        log.setTargetType(TARGET_TYPE);
        log.setTargetId(targetId);
        log.setScreenName(SCREEN_NAME);
        log.setApiName(API_NAME);
        log.setComment(comment);
        operationLogRepository.save(log);
    }
}
