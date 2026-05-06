package com.example.market.postal.runner;

import com.example.market.postal.service.ImportPostalCsvService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 郵便局 KEN_ALL.CSV 取込を「明示フラグ起動」で実行する Runner。
 *
 * 設計書 phase13 §7.2 では月次の Spring @Scheduled を採用予定だが、
 * フェーズ17 で組み込むまでは手動コマンド起動とする。
 *
 * 起動例:
 *   # ローカル（pom.xml の finalName=amazia-core により target/amazia-core.jar が生成される）
 *   java -jar target/amazia-core.jar --import-postal-csv
 *
 *   # 本番 EC2（既存 amazia-core コンテナを止めず、使い捨て JVM で実行）
 *   docker compose run --rm --no-deps amazia-core java -jar app.jar --import-postal-csv
 *
 * 注意:
 *   docker compose exec で既存コンテナに入って同 jar を再起動すると、Web サーバと
 *   ポート 8080 を奪い合って即死する。run --rm --no-deps を使うこと。
 *
 * 引数が無い通常起動では何もしないため、Web サーバ起動と共存する。
 */
@Component
public class ImportPostalCsvRunner implements ApplicationRunner {

    public static final String FLAG = "import-postal-csv";

    private static final Logger log = LoggerFactory.getLogger(ImportPostalCsvRunner.class);

    private final ImportPostalCsvService service;

    public ImportPostalCsvRunner(ImportPostalCsvService service) {
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption(FLAG)) {
            return;
        }
        log.info("[postal-csv-runner] flag --{} detected, executing import", FLAG);
        int saved = service.execute();
        log.info("[postal-csv-runner] import done saved={}", saved);
    }
}
