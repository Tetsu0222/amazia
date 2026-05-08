package com.example.faultinjection.service;

import com.example.batch.service.RandomGeneratorAdapter;
import com.example.delivery.entity.Delivery;
import com.example.delivery.repository.DeliveryRepository;
import com.example.inventory.service.InventoryAdjustmentService;
import com.example.sales.entity.Sales;
import com.example.sales.repository.SalesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * フェーズ17 Step 5-4: 配送トラブルトラブル関数（設計書 §4.2 ③ / R-2 / H-7 / G-1）。
 *
 * <p>対象限定（R-2）：{@code shipping_status_id = PENDING} の {@link Delivery} のみ。
 * {@code SHIPPED} 以降には注入しない（在庫減算フックが既に発火済のため）。
 *
 * <p>遷移先：{@code PENDING → CANCELED / DELIVERY_FAILED / RESCHEDULED} のいずれかをランダム選択。
 * {@code DeliveryStatusTransitionService} の遷移ガードはバイパスし {@link DeliveryRepository}
 * の直接呼び出しでステータスを書き換える。**バイパスを許すのはこの 1 ジョブのみ**。
 *
 * <p>補償 SKU TX（H-7 / R-2）：遷移と同時に必ず SKU TX に
 * {@code type='adjust', sku_id=sales.sku_id, quantity=+1, reference_type='fault_injection',
 * reference_id=sales.id, comment='[fault_injection][delivery][quantity_dummy] {status} simulation
 * (sales_id=N)'} を INSERT する。これがないと {@code InventoryConsistencyCheckJob} が
 * 翌朝以降毎回不整合を検出する事故になる。
 *
 * <p>{@code @Profile("!production")} により本番プロファイルでは Bean 化されない（五重防御の第 2 層）。
 */
@Profile("!production")
@Component
public class DeliveryTroubleInjector {

    private static final Logger log = LoggerFactory.getLogger(DeliveryTroubleInjector.class);

    public static final String INJECTOR_NAME = "DeliveryTroubleInjector";
    public static final String FAULT_INJECTION_REFERENCE_TYPE = "fault_injection";
    public static final String COMMENT_PREFIX = "[fault_injection][delivery][quantity_dummy]";

    /** 補償 SKU TX のダミー数量（設計書 §4.2 ③ で {@code +1} 固定）。 */
    public static final int COMPENSATION_QUANTITY = 1;

    private final DeliveryRepository deliveryRepository;
    private final SalesRepository salesRepository;
    private final InventoryAdjustmentService adjustment;
    private final RandomGeneratorAdapter random;
    private final FaultInjectionLogger logger;

    @Value("${amazia.simulation.fault-injection.enabled:false}")
    private boolean enabled;

    @Value("${amazia.simulation.fault-injection.delivery-trouble-rate:0.10}")
    private double troubleRate;

    @Value("${amazia.sales.shipping-statuses.pending-id}")
    private long pendingId;

    @Value("${amazia.sales.shipping-statuses.canceled-id}")
    private long canceledId;

    @Value("${amazia.sales.shipping-statuses.delivery-failed-id}")
    private long deliveryFailedId;

    @Value("${amazia.sales.shipping-statuses.rescheduled-id}")
    private long rescheduledId;

    public DeliveryTroubleInjector(DeliveryRepository deliveryRepository,
                                   SalesRepository salesRepository,
                                   InventoryAdjustmentService adjustment,
                                   RandomGeneratorAdapter random,
                                   FaultInjectionLogger logger) {
        this.deliveryRepository = deliveryRepository;
        this.salesRepository = salesRepository;
        this.adjustment = adjustment;
        this.random = random;
        this.logger = logger;
    }

    /**
     * scheduler 経路の確率発火（PENDING の deliveries のうち抽選で 1 件に注入）。
     */
    public int tryInject(String triggeredBy) {
        if (!enabled) return 0;
        if (random.nextDouble() >= troubleRate) return 0;
        return injectOnce(triggeredBy);
    }

    /**
     * 強制注入（{@code TriggerFaultInjectionJob} 用）。{@code PENDING} のいずれかを 1 件選び遷移させる。
     *
     * @return 注入対象になった {@link Delivery#getId()}。対象が無ければ 0
     */
    @Transactional
    public int injectOnce(String triggeredBy) {
        List<Delivery> pendings = deliveryRepository.findByShippingStatusId(pendingId);
        if (pendings.isEmpty()) {
            log.info("[{}] no PENDING delivery, skip injection", INJECTOR_NAME);
            return 0;
        }
        Delivery target = pendings.get(random.nextIntBetween(0, pendings.size() - 1));

        long nextStatusId = pickNextStatus();
        String statusLabel = labelFor(nextStatusId);

        // Repository 直接呼び出しでバリデーションをバイパス（この 1 ジョブのみに許される）
        Long previousStatusId = target.getShippingStatusId();
        target.setShippingStatusId(nextStatusId);
        deliveryRepository.saveAndFlush(target);

        // 補償 SKU TX（必須・H-7 / R-2）
        Sales sales = salesRepository.findById(target.getSalesId()).orElse(null);
        if (sales != null) {
            String comment = COMMENT_PREFIX + " " + statusLabel + " simulation (sales_id=" + sales.getId() + ")";
            adjustment.adjust(sales.getSkuId(), COMPENSATION_QUANTITY,
                    FAULT_INJECTION_REFERENCE_TYPE, sales.getId(), null, comment);
        } else {
            log.warn("[{}] sales not found for deliveryId={}, skip compensation SKU TX",
                    INJECTOR_NAME, target.getId());
        }

        logger.log(INJECTOR_NAME, triggeredBy,
                "deliveryId=" + target.getId()
                + ", salesId=" + target.getSalesId()
                + ", " + previousStatusId + " -> " + nextStatusId + " (" + statusLabel + ")");
        return target.getId().intValue();
    }

    private long pickNextStatus() {
        int pick = random.nextIntBetween(0, 2);
        return switch (pick) {
            case 0 -> canceledId;
            case 1 -> deliveryFailedId;
            default -> rescheduledId;
        };
    }

    private String labelFor(long statusId) {
        if (statusId == canceledId) return "CANCELED";
        if (statusId == deliveryFailedId) return "DELIVERY_FAILED";
        if (statusId == rescheduledId) return "RESCHEDULED";
        return "UNKNOWN";
    }
}
