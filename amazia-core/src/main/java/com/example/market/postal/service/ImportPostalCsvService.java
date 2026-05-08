package com.example.market.postal.service;

import com.example.market.postal.entity.PostalAddress;
import com.example.market.postal.repository.PostalAddressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 郵便局 KEN_ALL.CSV を取り込んで {@code postal_addresses} テーブルを全件洗い替えする Service。
 *
 * 設計書 phase13 §7.1 / §7.2 に対応。
 * - 取得元 URL は config（{@code postal.csv.url}）で差し替え可能
 * - 全件洗い替え方式（DELETE → batch INSERT）
 * - 失敗時は最大 3 回リトライ（指数バックオフ 1s → 2s → 4s）
 *
 * 起動方法:
 * - 定期実行: {@link com.example.batch.job.PostalCsvImportJob}（毎月 1 日 03:00 JST / @Scheduled）
 * - 手動実行: {@link com.example.market.postal.runner.ImportPostalCsvRunner} 経由のコマンド起動
 *   ローカル: {@code java -jar target/amazia-core.jar --import-postal-csv}
 *   本番:     {@code docker compose run --rm --no-deps amazia-core java -jar app.jar --import-postal-csv}
 */
@Service
public class ImportPostalCsvService {

    private static final Logger log = LoggerFactory.getLogger(ImportPostalCsvService.class);

    /** KEN_ALL.CSV は CP932（Shift_JIS 拡張）配布。 */
    private static final Charset CSV_CHARSET = Charset.forName("MS932");

    private static final int MAX_ATTEMPTS = 3;
    private static final int BATCH_SIZE = 1000;

    private final PostalAddressRepository repository;
    private final String csvUrl;

    public ImportPostalCsvService(PostalAddressRepository repository,
                                  @Value("${postal.csv.url}") String csvUrl) {
        this.repository = repository;
        this.csvUrl = csvUrl;
    }

    /**
     * URL からダウンロード → ZIP 展開 → CSV パース → 全件洗い替え。
     * 各段階の失敗で最大 3 回までリトライ。
     *
     * @return 投入件数
     */
    public int execute() {
        long start = System.currentTimeMillis();
        log.info("[postal-csv] import start url={}", csvUrl);
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                List<PostalAddress> rows = downloadAndParse();
                int saved = replaceAll(rows);
                log.info("[postal-csv] import success attempt={} saved={} elapsedMs={}",
                        attempt, saved, System.currentTimeMillis() - start);
                return saved;
            } catch (IOException | InterruptedException e) {
                last = (e instanceof IOException io) ? io : new IOException(e);
                log.warn("[postal-csv] import failed attempt={}/{} cause={}",
                        attempt, MAX_ATTEMPTS, e.getMessage());
                if (Thread.currentThread().isInterrupted()) break;
                if (attempt < MAX_ATTEMPTS) sleepBackoff(attempt);
            }
        }
        throw new IllegalStateException("[postal-csv] import failed after " + MAX_ATTEMPTS + " attempts", last);
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(1000L * (1L << (attempt - 1))); // 1s, 2s, 4s
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    List<PostalAddress> downloadAndParse() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(csvUrl))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();
        HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() / 100 != 2) {
            throw new IOException("CSV download failed status=" + res.statusCode() + " url=" + csvUrl);
        }
        try (InputStream body = res.body()) {
            return parse(body, csvUrl.toLowerCase().endsWith(".zip"));
        }
    }

    /**
     * 入力ストリームから PostalAddress のリストを構築。
     * ZIP ならエントリを開いて中の CSV を読む。直接 CSV なら素通し。
     * 可視性は package-private（テストから直接呼ぶため）。
     */
    List<PostalAddress> parse(InputStream raw, boolean isZip) throws IOException {
        if (isZip) {
            try (ZipInputStream zis = new ZipInputStream(raw)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().toLowerCase().endsWith(".csv")) {
                        return parseCsv(zis);
                    }
                }
                throw new IOException("no csv entry inside zip");
            }
        }
        return parseCsv(raw);
    }

    private List<PostalAddress> parseCsv(InputStream in) throws IOException {
        List<PostalAddress> rows = new ArrayList<>(140_000);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, CSV_CHARSET))) {
            String line;
            while ((line = reader.readLine()) != null) {
                PostalAddress row = parseLine(line);
                if (row != null) rows.add(row);
            }
        }
        return rows;
    }

    /**
     * KEN_ALL.CSV 1 行をパース。15 カラム想定でダブルクォート除去のみ行う簡易実装。
     * - col[2]: 郵便番号 7桁
     * - col[6]: 都道府県（漢字）
     * - col[7]: 市区町村（漢字）
     * - col[8]: 町域（漢字）
     * 注釈町域（"以下に掲載がない場合"・括弧内補足のみ）は捨てる。
     */
    PostalAddress parseLine(String line) {
        if (line == null || line.isBlank()) return null;
        String[] cols = line.split(",", -1);
        if (cols.length < 9) return null;
        String postalCode = unquote(cols[2]);
        String prefecture = unquote(cols[6]);
        String city = unquote(cols[7]);
        String town = unquote(cols[8]);
        if (postalCode.length() != 7 || !postalCode.chars().allMatch(Character::isDigit)) return null;
        if (prefecture.isEmpty() || city.isEmpty()) return null;
        town = sanitizeTown(town);
        if (town == null) return null;
        PostalAddress entity = new PostalAddress();
        entity.setPostalCode(postalCode);
        entity.setPrefecture(truncate(prefecture, 20));
        entity.setCity(truncate(city, 100));
        entity.setTown(truncate(town, 200));
        return entity;
    }

    private String unquote(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** 「以下に掲載がない場合」など住所として無意味な町域は捨てる。空文字を返したら捨てるシグナル。 */
    private String sanitizeTown(String town) {
        if (town == null) return null;
        String t = town.trim();
        if (t.isEmpty()) return ""; // 町域なしも有効（市区町村のみで完結する地域）として通す
        if ("以下に掲載がない場合".equals(t)) return "";
        return t;
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    @Transactional
    int replaceAll(List<PostalAddress> rows) {
        repository.deleteAllInBatch();
        // saveAll は内部で flush せず JPA バッチサイズに依存するため、件数で分割して OOM 回避
        int saved = 0;
        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, rows.size());
            repository.saveAll(rows.subList(i, end));
            saved += (end - i);
        }
        return saved;
    }
}
