package com.example.notice;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * フェーズ19 Step D / R19-10：`ops/healthcheck/required_tables.txt` に
 * フェーズ19 で追加した 3 テーブルが含まれていることを CI で物理担保する。
 *
 * <p>本番 MySQL に対する存在確認は GitHub Actions deploy.yml で別途実行されるが、
 * 「required_tables.txt 自体への登録漏れ」は CD ジョブが緑のまま素通りしてしまう
 * （存在しないテーブルが list にあれば検知されるが、不足は検知されない設計）。
 * 開発時の登録漏れを Core テスト緑チェックの段階で防ぐためのアサーション。
 */
class NoticeRequiredTablesConsistencyTest {

    private static final List<String> PHASE19_TABLES = List.of(
            "notice_categories",
            "notices",
            "notice_reads"
    );

    @Test
    void required_tables_txt_に_phase19_の_3_テーブルが_列挙されている() throws IOException {
        Path file = locateRequiredTablesFile();
        Set<String> actual = Files.readAllLines(file).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .collect(Collectors.toUnmodifiableSet());

        for (String table : PHASE19_TABLES) {
            assertTrue(actual.contains(table),
                    "required_tables.txt に '" + table + "' が含まれていない（フェーズ19 Step D / R19-10）。"
                            + " 不足: " + table);
        }
    }

    /**
     * `ops/healthcheck/required_tables.txt` の絶対パスを解決する。
     *
     * <p>テストは {@code amazia-core/} を作業ディレクトリとして実行されるため、
     * リポジトリルートまで一階層上がってから {@code ops/healthcheck/...} を辿る。
     */
    private Path locateRequiredTablesFile() {
        Path candidate = Paths.get("..", "ops", "healthcheck", "required_tables.txt").toAbsolutePath().normalize();
        assertTrue(Files.exists(candidate),
                "required_tables.txt が見つからない: " + candidate);
        return candidate;
    }
}
