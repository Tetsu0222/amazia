package com.example.inbound.service;

import com.example.delivery.service.DeliveryRescheduleService;
import com.example.inbound.dto.RegisterInboundRequest;
import com.example.inbound.entity.Inbound;
import com.example.inbound.repository.InboundRepository;
import com.example.inventory.service.InventorySyncService;
import com.example.operationlog.entity.OperationLog;
import com.example.operationlog.repository.OperationLogRepository;
import com.example.product.repository.ProductRepository;
import com.example.sku.entity.ProductSku;
import com.example.sku.repository.ProductSkuRepository;
import com.example.sku.service.ReceiveProductSkuStockService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 入荷登録 Service（フェーズ15 r5 / P5-5 / R-3）。
 *
 * <p>責務（規約 1-1）：
 * <ol>
 *   <li>バリデーション（{@code product_id} / {@code sku_id} 存在 / 親商品との整合性）</li>
 *   <li>{@code inbounds} レコード INSERT</li>
 *   <li>既存 {@link ReceiveProductSkuStockService} を呼び出して {@code product_sku_stocks} を加算</li>
 *   <li>{@code inventories.quantity} 加算（並行運用 / RRRR-2）— Step B-5 で {@code InventorySyncService.applyDelta} を DI</li>
 *   <li>{@link com.example.delivery.service.DeliveryRescheduleService#recalculateForProduct(Long)}
 *       を呼び出して在庫切れ {@code deliveries} を FIFO 再計算 — Step B-4 で DI</li>
 *   <li>{@code operation_logs.action='register_inbound'} 記録</li>
 * </ol>
 *
 * <p>本 Step B-3 では (1)-(3)・(6) を実装する。(4) と (5) は依存先 Service が
 * 完成次第（B-5 / B-4）DI で接続する。
 */
@Service
public class RegisterInboundService {

    private static final String ACTION = "register_inbound";
    private static final String TARGET_TYPE = "inbounds";
    private static final String SCREEN_NAME = "console.inbound.register";
    private static final String API_NAME = "POST /api/inbounds";

    private final InboundRepository inboundRepository;
    private final ProductRepository productRepository;
    private final ProductSkuRepository skuRepository;
    private final ReceiveProductSkuStockService receiveProductSkuStockService;
    private final InventorySyncService inventorySyncService;
    private final DeliveryRescheduleService deliveryRescheduleService;
    private final OperationLogRepository operationLogRepository;

    private final long defaultWarehouseId;

    public RegisterInboundService(
            InboundRepository inboundRepository,
            ProductRepository productRepository,
            ProductSkuRepository skuRepository,
            ReceiveProductSkuStockService receiveProductSkuStockService,
            InventorySyncService inventorySyncService,
            DeliveryRescheduleService deliveryRescheduleService,
            OperationLogRepository operationLogRepository,
            @Value("${amazia.delivery.default-warehouse-id}") long defaultWarehouseId) {
        this.inboundRepository = inboundRepository;
        this.productRepository = productRepository;
        this.skuRepository = skuRepository;
        this.receiveProductSkuStockService = receiveProductSkuStockService;
        this.inventorySyncService = inventorySyncService;
        this.deliveryRescheduleService = deliveryRescheduleService;
        this.operationLogRepository = operationLogRepository;
        this.defaultWarehouseId = defaultWarehouseId;
    }

    /**
     * 入荷を登録する。
     *
     * @param request     入荷登録リクエスト
     * @param actorUserId 操作者 ID（{@code users.id}）
     * @return 登録された {@link Inbound}
     */
    @Transactional
    public Inbound register(RegisterInboundRequest request, Long actorUserId) {
        // 1. バリデーション
        if (productRepository.findById(request.getProductId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found");
        }
        ProductSku sku = skuRepository.findById(request.getSkuId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "sku not found"));
        if (!sku.getProductId().equals(request.getProductId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "sku does not belong to product");
        }

        // 2. inbounds INSERT（warehouse_id は config 値を自動セット / RRRR-5）
        Inbound inbound = new Inbound();
        inbound.setProductId(request.getProductId());
        inbound.setWarehouseId(defaultWarehouseId);
        inbound.setSupplierId(request.getSupplierId());
        inbound.setQuantity(request.getQuantity());
        inbound.setInboundedAt(request.getInboundedAt());
        Inbound saved = inboundRepository.saveAndFlush(inbound);

        // 3. SKU 在庫加算（既存 Service を再利用）
        receiveProductSkuStockService.receive(request.getSkuId(), request.getQuantity());

        // 4. inventories 同期加算（並行運用 / RRRR-2）
        inventorySyncService.applyDelta(request.getProductId(), defaultWarehouseId,
                request.getQuantity());

        // 5. deliveries.scheduled_date FIFO 再計算（RRR-4 / RRRR-4）
        deliveryRescheduleService.recalculateForProduct(request.getProductId(), actorUserId);

        // 6. operation_logs 記録
        recordLog(actorUserId, saved.getId(),
                "商品ID=" + request.getProductId()
                        + ", SKU ID=" + request.getSkuId()
                        + ", 数量=" + request.getQuantity()
                        + ", 倉庫=" + defaultWarehouseId);

        return saved;
    }

    private void recordLog(Long actorUserId, Long inboundId, String comment) {
        OperationLog log = new OperationLog();
        log.setUserId(actorUserId);
        log.setAction(ACTION);
        log.setTargetType(TARGET_TYPE);
        log.setTargetId(inboundId);
        log.setScreenName(SCREEN_NAME);
        log.setApiName(API_NAME);
        log.setComment(comment);
        operationLogRepository.save(log);
    }
}
