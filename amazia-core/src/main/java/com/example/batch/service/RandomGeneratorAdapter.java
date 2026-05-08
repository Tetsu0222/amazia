package com.example.batch.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * フェーズ17 Step 3-3: 乱数生成のテスト差し替えポイント（R-14 / 設計書 §3.1 ③）。
 *
 * <p>本番では {@link ThreadLocalRandom} を使い、テストでは Mockito で固定値を返す
 * モックに差し替える。{@code SalesReconciliationJob} の振込確認確率発火を決定的にできる。
 */
@Component
public class RandomGeneratorAdapter {

    /** {@code [0.0, 1.0)} の乱数を返す。 */
    public double nextDouble() {
        return ThreadLocalRandom.current().nextDouble();
    }
}
