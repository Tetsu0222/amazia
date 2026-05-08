package com.example.batch.job;

import com.example.batch.config.AbstractBatchJob;
import com.example.batch.config.BatchResult;
import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * フェーズ17 Step 3-2: 発売日・予約ステータスの自動更新（設計書 §3.1 ②）。
 *
 * <p>予約ステータス判定は phase14 でリアルタイム化済み。本ジョブは
 * 「公開日／発売日／予約開始日が今日と一致した商品」を抽出して、
 * 将来導入される検索インデックス更新フックの起点を確保する役割のみを担う。
 * Spring Cache 等の外部キャッシュ無効化は本フェーズではスコープ外。
 *
 * <p>商品マスタの状態は書き換えない（YAGNI）。失敗時のみ通知。
 */
@Component
@ConditionalOnProperty(name = "amazia.batch.scheduler-enabled",
                       havingValue = "true", matchIfMissing = true)
public class PreorderStatusRefreshJob extends AbstractBatchJob {

    private static final Logger log = LoggerFactory.getLogger(PreorderStatusRefreshJob.class);
    public static final String JOB_NAME = "PreorderStatusRefreshJob";

    private final ProductRepository productRepository;

    public PreorderStatusRefreshJob(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Scheduled(cron = "${amazia.batch.daily.cron}", zone = "${amazia.batch.timezone}")
    public void scheduledRun() {
        run("scheduler");
    }

    @Override
    protected String jobName() { return JOB_NAME; }

    @Override
    protected BatchResult execute() {
        LocalDate today = LocalDate.now();
        List<Product> products = productRepository.findAll();
        List<Product> hits = products.stream()
                .filter(p -> matchesToday(p, today))
                .toList();
        log.info("[{}] {} products matched today ({})", JOB_NAME, hits.size(), today);
        return BatchResult.of(hits.size(), hits.size(), 0);
    }

    public static boolean matchesToday(Product product, LocalDate today) {
        if (product.getReleaseDate() != null && product.getReleaseDate().isEqual(today)) return true;
        if (product.getPreorderStartDate() != null && product.getPreorderStartDate().isEqual(today)) return true;
        if (product.getPublishStart() != null && product.getPublishStart().toLocalDate().isEqual(today)) return true;
        return false;
    }
}
