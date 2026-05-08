package com.example.batch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * フェーズ17 Step 4: バッチで「現在時刻」をテスト容易に注入するための {@link Clock} Bean。
 *
 * <p>本番では {@code amazia.batch.timezone}（既定 {@code Asia/Tokyo}）のシステムクロック。
 * テストでは {@code @TestConfiguration} で固定クロックに差し替えられる。
 */
@Configuration
public class BatchClockConfig {

    @Bean
    public Clock batchClock(@Value("${amazia.batch.timezone:Asia/Tokyo}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
