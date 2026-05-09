# 052: weekly random-order CI 失敗 — `InventoryInconsistencyToNotificationE2ETest` の `roleRepository.findByCode("admin")` が空

## ステータス
🟡 対応中（2026-05-09）

## 発症箇所
- CI: GitHub Actions `Weekly Test Random Order` ワークフロー（[weekly-test-random-order.yml](../../.github/workflows/weekly-test-random-order.yml)）
- 失敗テスト: [InventoryInconsistencyToNotificationE2ETest.E2E_1_不整合データから_SES送信_と_通知センター未読_が同時発火する:87](../../amazia-core/src/test/java/com/example/batch/e2e/InventoryInconsistencyToNotificationE2ETest.java#L87) → 内部ヘルパ [`persistAdminWithSubscription:151`](../../amazia-core/src/test/java/com/example/batch/e2e/InventoryInconsistencyToNotificationE2ETest.java#L151)
- ローカル `mvn test` ではグリーン（`target/surefire-reports/TEST-...InventoryInconsistencyToNotificationE2ETest.xml` で `tests=1, errors=0, failures=0` を確認）
- 週次 random 順序 CI でのみ顕在化

## 症状
```
Error: InventoryInconsistencyToNotificationE2ETest.E2E_1_不整合データから_SES送信_と_通知センター未読_が同時発火する:87
        ->persistAdminWithSubscription:151 » NoSuchElement No value present
[INFO] Tests run: 534, Failures: 0, Errors: 1, Skipped: 0
```

`persistAdminWithSubscription` の先頭で `roleRepository.findByCode("admin").orElseThrow()` を呼んでおり、`admin` ロールが見つからずに `NoSuchElementException: No value present` が発生する。

## 根本原因

051 派生②と同型の **「H2 共有 × `@Transactional` 不在テスト × random 順序」** に起因するテスト分離の崩れ。

### 構造

[application-test.properties:1](../../amazia-core/src/test/resources/application-test.properties#L1) は `jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1` で **JVM 終了まで H2 を共有**する。一方で [InventoryInconsistencyToNotificationE2ETest:65-77](../../amazia-core/src/test/java/com/example/batch/e2e/InventoryInconsistencyToNotificationE2ETest.java#L65-L77) はクラスレベル `@Transactional` を **意図的に外している**（クラス JavaDoc：「`BatchAlertNotifier` は REQUIRES_NEW で console_notifications に書き込むため、@Transactional ロールバックを貫通する」）。

通常 `roles` テーブルは [test-data.sql:6](../../amazia-core/src/test/resources/test-data.sql#L6) で context 起動時に INSERT され、cleanup SQL もこれを TRUNCATE しないため安定して残る。ただし以下のような外乱要因の組み合わせで `findByCode("admin")` が空を返すケースが発生しうる：

- **複数 ApplicationContext のキャッシュ／再生成**：`@SpringBootTest(properties = ...)` で properties キーが異なるクラスは別 context として起動し、その都度 `ddl-auto=create-drop` が `roles` を含む全テーブルを DROP & CREATE する。共有 `mem:testdb` 上で他コンテキストが先に動いている最中に DROP が走れば、そのテストから見ると `roles` が一瞬空になる
- **random 順序での隣接効果**：surefire の `runOrder=random` で、上記の context 切り替えと当該 E2E テストの実行が運悪く接近する

ローカル surefire（filesystem 順序）ではこの並びを踏まないため再現せず、CI Linux ランナー × random 順序でのみ顕在化する典型パターン。051 派生②でも同型の「ローカル PASS / CI のみ FAIL」を学習済み。

### `@Transactional` 付与で解決しない理由

クラス JavaDoc が明記している通り、本テストは **`BatchAlertNotifier`（`@Transactional(REQUIRES_NEW)`）が `console_notifications` に書き込んだ未読 1 件をテスト終了後に検証**する設計。テストクラスに `@Transactional` を付けると outer TX のロールバックは効くが、`REQUIRES_NEW` の書き込みは貫通して残置するため、`@Transactional` で分離する選択肢は取れない（051 派生③で同じ判断を経験済み）。

phaseX-9 の cleanup.sql 規約では「観測対象テーブル群」のみを TRUNCATE する設計のため、`roles`（マスタデータ）は cleanup 対象外で問題ないが、**`roles` を読む側のテストヘルパーは fallback を持つべき**だった。

## なぜ CI で検知できなかったか
- 当該テストは E2E パッケージ内で実装され、ローカル `mvn test` 全件 PASS（surefire-reports XML で確認済み）。
- 週次 random 順序 CI は phaseX-9 で**まさにこの種の flaky を捕まえる目的で稼働中**であり、設計通り検知に成功した。継続検知のためには再発防止と並んで「拾われた flaky を抹本対策に落とす」運用が必要。

## 修正内容

### 修正① `persistAdminWithSubscription` に admin ロール ensure ロジック

[InventoryInconsistencyToNotificationE2ETest.java:150-167](../../amazia-core/src/test/java/com/example/batch/e2e/InventoryInconsistencyToNotificationE2ETest.java#L150-L167) の `persistAdminWithSubscription` を、**`admin` ロールが見つからなかった場合は作る**形に書き換え：

```java
private long persistAdminWithSubscription(String email) {
    Role admin = roleRepository.findByCode("admin").orElseGet(() -> {
        Role r = new Role();
        r.setCode("admin");
        r.setName("管理者");
        return roleRepository.save(r);
    });
    // ... 以下既存処理
}
```

phaseX-9 で確立した「**汚染受け側の自衛**」パターン。クラス `@Transactional` を付けられない設計上、テストヘルパー側で「マスタデータが期待通り存在しなければ自分で揃える」という防御を入れる。

### 修正② Role エンティティに setter を追加

[Role.java](../../amazia-core/src/main/java/com/example/auth/entity/Role.java) は getter のみで setter が無いため、テストから新規 Role を作るには setter が必要。プロダクションコードへの影響を最小化するため `setCode` / `setName` のみ追加。

## 再発防止

| 観点 | 対策 |
|------|------|
| **マスタデータを参照するテストヘルパーは「無ければ作る」フォールバックを持つ** | phaseX-9 が cleanup.sql で「観測対象テーブルのみ TRUNCATE」する設計を選んだ結果、マスタテーブル（roles / permissions / payment_methods / shipping_statuses 等）は context 共有で残る前提になっている。一方で context 切り替えや DDL 再生成で**一瞬空になる**ケースを拾えていなかった。`test_insights.md` カテゴリ 7-2 にこの観点を追記する |
| **`@Transactional` を付けられない E2E は「自衛コード」が必須** | 051 派生③で確立した「`REQUIRES_NEW` 検証目的で `@Transactional` を外す」テストは、自テストヘルパー内で `findByCode().orElseThrow()` 系の決め打ちを避ける。同型コードを E2E パッケージ内で grep して横展開チェック |
| **週次 random 順序 CI の Issue 起票後の運用** | `weekly-test-random-order.yml` が flaky を検知して Issue 起票したら、当該フェーズ内で対応 → 052 のように個別トラブル記録に落とす。Issue 自動起票の文面（[`weekly-test-random-order.yml:47-60`](../../.github/workflows/weekly-test-random-order.yml#L47-L60)）に「**修正後は対応するトラブル記録 NNN_*.md を作成し AP-009 / TPL-009 を確認**」のリンクを追加検討 |

## AI協働観点
- **AI の判断ミス**：当該テスト実装時、`persistAdminWithSubscription` で `findByCode("admin").orElseThrow()` をそのまま書いた。test-data.sql で admin ロールを INSERT しているのは事実だが、**「context 共有 H2 上で他テストの DDL 再生成や surefire random 順序の影響でマスタが一瞬空になる」**観点が抜けていた。phaseX-9 の AP-009 で「テスト分離不足」観点は整備されたが、**マスタデータ参照側の防御**まで踏み込んでいなかった
- **人間が止めるべきだった点**：phaseX-9 の cleanup.sql 規約棚卸し時に「マスタデータを参照する E2E テスト群」を別軸で見ていれば、本件は事前検知できた。しかし phaseX-9 自体が 051 派生①〜③の症状療法を抹本対策に集約するスコープだったため、観点が混在しなかったとも言える
- **該当アンチパターン**：[AP-009「テスト分離不足 + 単発 PR で類似クラス見落とし」](../ai_context/ai_collaboration_antipatterns.md#ap-009-テスト分離不足--単発-pr-で類似クラス見落とし) のサブパターン。「`REQUIRES_NEW` 貫通検証で `@Transactional` を外したテスト」は phaseX-9 で 6 クラスとして整理済みだが、**それらのテスト内のマスタデータ参照箇所**を抜き出した横断観点はまだ無い。AP-009 への追記候補
