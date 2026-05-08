package com.example.batch.job;

import com.example.batch.config.AbstractBatchJob;
import com.example.batch.config.BatchResult;
import com.example.batch.config.OnDemandJob;
import com.example.faultinjection.service.DeliveryTroubleInjector;
import com.example.faultinjection.service.InventoryMismatchInjector;
import com.example.faultinjection.service.SalesMismatchInjector;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * フェーズ17 Step 5-5: フォルトインジェクション一括起動ジョブ（設計書 §3.4 / §4.1.2 / M-4）。
 *
 * <p>{@link SalesMismatchInjector} / {@link InventoryMismatchInjector} /
 * {@link DeliveryTroubleInjector} を 1 回ずつ強制発火させるオンデマンドジョブ。
 *
 * <p>本番では {@code @Profile("!production")} により Bean が DI に登録されないため、
 * {@code POST /api/console/batch/TriggerFaultInjectionJob/run} は
 * {@link com.example.batch.controller.BatchManualTriggerController} が
 * {@code Map<String, OnDemandJob>} ルックアップで {@code null} を返し HTTP 404 を返す（M-4）。
 */
@Profile("!production")
@Component(TriggerFaultInjectionJob.JOB_NAME)
public class TriggerFaultInjectionJob extends AbstractBatchJob implements OnDemandJob {

    private static final Logger log = LoggerFactory.getLogger(TriggerFaultInjectionJob.class);
    public static final String JOB_NAME = "TriggerFaultInjectionJob";

    private final SalesMismatchInjector salesInjector;
    private final InventoryMismatchInjector inventoryInjector;
    private final DeliveryTroubleInjector deliveryInjector;
    private final ProductRepository productRepository;

    public TriggerFaultInjectionJob(SalesMismatchInjector salesInjector,
                                    InventoryMismatchInjector inventoryInjector,
                                    DeliveryTroubleInjector deliveryInjector,
                                    ProductRepository productRepository) {
        this.salesInjector = salesInjector;
        this.inventoryInjector = inventoryInjector;
        this.deliveryInjector = deliveryInjector;
        this.productRepository = productRepository;
    }

    @Override
    public String jobName() { return JOB_NAME; }

    @Override
    protected BatchResult execute() {
        String triggeredBy = "manual:TriggerFaultInjectionJob";

        salesInjector.injectOnce(triggeredBy);
        int salesCount = 1;

        int inventoryCount = 0;
        Long firstProductId = pickFirstProductId();
        if (firstProductId != null) {
            int delta = inventoryInjector.inject(firstProductId, triggeredBy);
            if (delta != 0) inventoryCount = 1;
        } else {
            log.warn("[{}] no product available for inventory injection", JOB_NAME);
        }

        int deliveryCount = deliveryInjector.injectOnce(triggeredBy) > 0 ? 1 : 0;

        int target = 3;
        int success = salesCount + inventoryCount + deliveryCount;
        int failure = target - success;
        log.info("[{}] sales={}, inventory={}, delivery={}",
                JOB_NAME, salesCount, inventoryCount, deliveryCount);
        return BatchResult.of(target, success, failure);
    }

    private Long pickFirstProductId() {
        List<Product> products = productRepository.findAll();
        return products.isEmpty() ? null : products.get(0).getId();
    }
}
