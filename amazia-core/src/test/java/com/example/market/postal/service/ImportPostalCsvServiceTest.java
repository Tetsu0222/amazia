package com.example.market.postal.service;

import com.example.market.postal.entity.PostalAddress;
import com.example.market.postal.repository.PostalAddressRepository;
import com.example.shared.config.TestAwsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 郵便局 CSV 取込 Service の単体テスト。
 * フェーズ17 でバッチ化するまでは parser / replaceAll のみカバーする
 * （ネットワーク経由のダウンロードは外部依存のためテスト対象外）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestAwsConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ImportPostalCsvServiceTest {

    private static final Charset CP932 = Charset.forName("MS932");

    @Autowired ImportPostalCsvService service;
    @Autowired PostalAddressRepository repository;

    private static String sampleLine(String code, String pref, String city, String town) {
        // KEN_ALL.CSV の 15 カラム形式を簡易再現。col[2]=postalCode, col[6]=pref, col[7]=city, col[8]=town
        return String.join(",",
                "01101", "060  ", "\"" + code + "\"",
                "\"ホッカイドウ\"", "\"サッポロシ\"", "\"カナチョウ\"",
                "\"" + pref + "\"", "\"" + city + "\"", "\"" + town + "\"",
                "0", "0", "0", "0", "0", "0");
    }

    @Test
    void parseLineで漢字カラムを取り出せること() {
        var entity = service.parseLine(sampleLine("1000001", "東京都", "千代田区", "千代田"));
        assertThat(entity).isNotNull();
        assertThat(entity.getPostalCode()).isEqualTo("1000001");
        assertThat(entity.getPrefecture()).isEqualTo("東京都");
        assertThat(entity.getCity()).isEqualTo("千代田区");
        assertThat(entity.getTown()).isEqualTo("千代田");
    }

    @Test
    void parseLineで以下に掲載がない場合の町域は空文字に正規化されること() {
        var entity = service.parseLine(sampleLine("0600000", "北海道", "札幌市中央区", "以下に掲載がない場合"));
        assertThat(entity).isNotNull();
        assertThat(entity.getTown()).isEmpty();
    }

    @Test
    void parseLineで桁数不正な郵便番号はnullが返ること() {
        assertThat(service.parseLine(sampleLine("12345", "東京都", "千代田区", "千代田"))).isNull();
    }

    @Test
    void parseLineで空行はnullが返ること() {
        assertThat(service.parseLine("")).isNull();
        assertThat(service.parseLine(null)).isNull();
    }

    @Test
    void parseでZIP内CSVを読めること() throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append(sampleLine("1000001", "東京都", "千代田区", "千代田")).append('\n');
        csv.append(sampleLine("1000002", "東京都", "千代田区", "皇居外苑")).append('\n');

        byte[] zipped = toZip("KEN_ALL.CSV", csv.toString().getBytes(CP932));

        List<PostalAddress> rows;
        try (InputStream in = new ByteArrayInputStream(zipped)) {
            rows = service.parse(in, true);
        }

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getPostalCode()).isEqualTo("1000001");
        assertThat(rows.get(1).getTown()).isEqualTo("皇居外苑");
    }

    @Test
    void parseで生CSVも読めること() throws IOException {
        String csv = sampleLine("1000001", "東京都", "千代田区", "千代田") + "\n";
        try (InputStream in = new ByteArrayInputStream(csv.getBytes(CP932))) {
            List<PostalAddress> rows = service.parse(in, false);
            assertThat(rows).hasSize(1);
        }
    }

    @Test
    void replaceAllで全件洗い替えされること() {
        // 既存データを投入
        PostalAddress old = new PostalAddress();
        old.setPostalCode("0000000");
        old.setPrefecture("旧県");
        old.setCity("旧市");
        old.setTown("旧町");
        repository.save(old);
        assertThat(repository.count()).isEqualTo(1);

        PostalAddress neu = new PostalAddress();
        neu.setPostalCode("1000001");
        neu.setPrefecture("東京都");
        neu.setCity("千代田区");
        neu.setTown("千代田");

        int saved = service.replaceAll(List.of(neu));

        assertThat(saved).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findByPostalCode("0000000")).isEmpty();
        assertThat(repository.findByPostalCode("1000001")).hasSize(1);
    }

    // execute() は HTTP ダウンロード〜パース〜永続化のオーケストレーション。
    // 実 URL に依存させず、ネットワーク経由のテストはフェーズ17 のバッチ化時に統合テストとして追加する方針。
    // 本フェーズでは parseLine / parse / replaceAll の単体検証で品質担保する。

    private byte[] toZip(String entryName, byte[] content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
