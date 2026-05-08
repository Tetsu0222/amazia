package com.example.faultinjection.service;

import com.example.batch.service.RandomGeneratorAdapter;
import com.example.inventory.service.InventoryAdjustmentService;
import com.example.sku.entity.ProductSku;
import com.example.sku.repository.ProductSkuRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * フェーズ17 Step 5-3: 在庫数不一致トラブル関数（設計書 §4.2 ② / H-7 / G-1）。
 *
 * <p>対象 {@code productId} 配下の最若 SKU を選び、{@code -3 〜 +3}（0 を除く）の {@code delta} で
 * {@link InventoryAdjustmentService#adjust} を呼ぶ。Service 層契約により SKU TX
 * （{@code type='adjust'}）と {@code product_sku_stocks.quantity} と {@code inventories.quantity} が
 * 同時に書き換わる。
 *
 * <p>SKU TX には {@code reference_type='fault_injection'} と
 * {@code comment='[fault_injection][inventory] simulated drift'} が記録され、
 * {@link com.example.batch.job.InventoryConsistencyCheckJob} が後で「人為注入」と区別できる。
 *
 * <p>{@code @Profile("!production")} により本番プロファイルでは Bean 化されない（五重防御の第 2 層）。
 */
@Profile("!production")
@Component
public class InventoryMismatchInjector {

    private static final Logger log = LoggerFactory.getLogger(InventoryMismatchInjector.class);

    public static final String INJECTOR_NAME = "InventoryMismatchInjector";
    public static final String FAULT_INJECTION_REFERENCE_TYPE = "fault_injection";
    public static final String COMMENT_PREFIX = "[fault_injection][inventory]";

    private final InventoryAdjustmentService adjustment;
    private final ProductSkuRepository skuRepository;
    private final RandomGeneratorAdapter random;
    private final FaultInjectionLogger logger;

    @Value("${amazia.simulation.fault-injection.enabled:false}")
    private boolean enabled;

    @Value("${amazia.simulation.fault-injection.inventory-mismatch-rate:0.05}")
    private double mismatchRate;

    public InventoryMismatchInjector(InventoryAdjustmentService adjustment,
                                     ProductSkuRepository skuRepository,
                                     RandomGeneratorAdapter random,
                                     FaultInjectionLogger logger) {
        this.adjustment = adjustment;
        this.skuRepository = skuRepository;
        this.random = random;
        this.logger = logger;
    }

    /**
     * 確率発火判定を行い、当選した場合のみ {@link #inject(long, String)} を呼ぶ。
     * scheduler から呼ばれる経路（在庫整合性チェック等）はこちらを使う。
     *
     * @return 発火したら {@code true}、しなかったら {@code false}
     */
    public boolean tryInject(long productId, String triggeredBy) {
        if (!enabled) return false;
        if (random.nextDouble() >= mismatchRate) return false;
        return inject(productId, triggeredBy) != 0;
    }

    /**
     * 確率を無視して 1 回だけ強制注入する（{@code TriggerFaultInjectionJob} 用）。
     *
     * @return 適用した {@code delta}（対象 SKU が存在しなかった場合は 0）
     */
    public int inject(long productId, String triggeredBy) {
        List<ProductSku> skus = skuRepository.findByProductIdOrderByIdAsc(productId);
        if (skus.isEmpty()) {
            log.warn("[{}] no SKU found for productId={}, skip injection", INJECTOR_NAME, productId);
            return 0;
        }
        Long skuId = skus.get(0).getId();
        int delta = random.nextIntBetween(-3, 3);
        if (delta == 0) delta = 1; // Service 層契約：adjust の quantity != 0
        String comment = COMMENT_PREFIX + " simulated drift (productId=" + productId
                + ", skuId=" + skuId + ", delta=" + delta + ")";
        adjustment.adjust(skuId, delta, FAULT_INJECTION_REFERENCE_TYPE, null, null, comment);
        logger.log(INJECTOR_NAME, triggeredBy,
                "productId=" + productId + ", skuId=" + skuId + ", delta=" + delta);
        return delta;
    }
}
