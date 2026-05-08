# フェーズX-21：Testcontainers 移行による本番 DB 等価テスト基盤

## ステータス
🟡 スコープ定義（着手未確定）

phaseX-9 Step 6 で作成（2026-05-09）。本フェーズは phaseX-9 で残った構造的妥協点と、H2 ドリフト系トラブル群（027/037/038/044/045/049/050）の構造的解決を目的とする独立フェーズ。実装フェーズへの移行は別途レビュー後に判断する。

> **暫定番号**: 「Phase 21」は仮称。正式番号は時系列フェーズ進行（現在 phase16〜18 計画中）に応じて確定する。

## 位置付け
時系列フェーズ（1〜20）に依存しない横断的品質改善フェーズ。phaseX-6（スキーマ層の構造的盲点）/ phaseX-7（AI 協働層の構造的盲点）/ phaseX-9（テスト分離の構造的盲点）と同系列で、**「テスト DB と本番 DB の乖離」という構造的盲点** に対する抹本対策として位置付ける。

phaseX-9 では H2 共有 DB 上で「cleanup.sql + `@Sql`」方式によりテスト分離を確立したが、以下 2 点の構造的妥協が残った:

1. **テスト DB と本番 DB の DDL 互換性差**: H2 と MySQL は DDL 構文・型概念（UNSIGNED 等）・制約挙動・SQL 関数で乖離があり、CI（H2）で PASS しても本番（MySQL）で破綻する不具合が継続発生（H2 ドリフト系 7 件）。
2. **phaseX-9 で `@Sql` 適用しきれなかった 4 クラス**: テーブル間アーカイブ系 2 + マルチスレッド検証 1 + DB 非依存 1。cleanup.sql + `@Sql` 方式では構造的解決できない。

本フェーズはこれらを **Testcontainers + per-class MySQL** で抹本解決する。

---

## 背景・なぜ今やるか

### 構造的盲点

```
テスト DB（H2 in-memory）と本番 DB（MySQL 8.x）の DDL 互換性差
  └─ Entity 定義 / schema.sql 記述の検証が H2 でしか行われない
      └─ 本番 MySQL 固有の挙動（UNSIGNED / NOT NULL / FK 型整合 / SQL 関数）を CI で検出不能
          └─ 本番デプロイ後に 500 エラー / DDL 失敗で初めて顕在化
              └─ 個別不具合は記録されるが、根本原因は H2 共有のため放置
                  └─ 同型のドリフト不具合が別テーブル / 別カラムで再発（044→045→049 系列）
```

### H2 ドリフト系トラブル一覧（出典）

phaseX-9 起因のテスト分離問題（051）と並んで、本フェーズが構造的に解決すべき過去トラブル:

| # | トラブル | 症状 | H2 で検知できなかった理由 |
|---|---|---|---|
| [027](../../troubles/027_workflow_test_h2_schema_and_json_payload.md) | ワークフロー導入で CI 全滅 | H2 の JSON 列マッピング差 | H2 が JSON 型を扱う方法が MySQL と異なる |
| [037](../../troubles/037_flyway_misassumed_phase14_tables_missing.md) | Flyway 利用と誤認しテーブル不在 | 本番 MySQL に必要テーブルなし | H2 は Entity から自動生成のため気付けず |
| [038](../../troubles/038_products_price_stock_not_null_drift.md) | products.price / stock の NOT NULL ドリフト | 本番 MySQL で 500 | H2 と本番で DDL 適用順 / NOT NULL 制約の差 |
| [044](../../troubles/044_operation_logs_table_missing_users_id_unsigned_drift.md) | operation_logs テーブル不在 + UNSIGNED ドリフト | 本番 MySQL で 500 | H2 に UNSIGNED 概念なし |
| [045](../../troubles/045_sales_return_table_missing_users_id_unsigned_drift.md) | sales_return 同型再発 | 本番 MySQL で 500 | 同上（044 修正時の横展開漏れ） |
| [049](../../troubles/049_password_histories_table_missing_in_schema_sql.md) | password_histories 不在（044/045 同型・phaseX-6 で事前検知） | デプロイ後検知 | 同上（schema.sql 自体の記載漏れ） |
| [050](../../troubles/050_h2_unique_constraint_test_helper_collision.md) | UNIQUE 制約衝突（H2 ドリフトの "逆向き" 顕在化） | CI 全滅 | H2 が Entity 通りに作るため逆向きに落ちる |

**全 7 件すべて個別では解決済み**だが、**構造的根本原因（H2/MySQL 乖離）は放置されたまま**で、新規スキーマ追加時に同型再発のリスクが残る。phaseX-6 のヘルスチェック（required_tables.txt）は事後検知であり、構造的根絶には Testcontainers 移行が必要。

### phaseX-9 から引継いだ妥協点

[phaseX-9_concession_inventory.md](../../implementation/phaseX-9_concession_inventory.md) の Phase 21 申送り 4 クラス + cleanup.sql 機構自体の MySQL 互換性問題:

#### 引継ぎ事項 1: テーブル間アーカイブ系 2 クラス
- `com.example.batch.ConsoleNotificationsArchiveJobTest`
- `com.example.batch.OperationLogArchiveJobTest`

両クラスとも **`@Transactional` 不在 + 2 テーブル間の移送（`*_archives` への INSERT）を検証**。`@Transactional` を付けると ARCHIVE 側 INSERT がロールバックで観測不可、付けないと他テストへの汚染源。phaseX-9 の cleanup.sql + `@Sql` 方式では「移送途中の状態」を観測するロジックが構造的に成立せず、**コンテキスト分離（per-class MySQL）が正しい解**。

#### 引継ぎ事項 2: マルチスレッド検証 1 クラス
- `com.example.batch.BatchJobLockRegistryTest`

`CountDownLatch` で 2 スレッド同時実行・ロック取得競争を観察。クラスレベル `@Transactional` と根本的に両立不可（同一 Tx 内の並列実行は分離されない）。cleanup.sql で `batch_executions` を毎テスト TRUNCATE は可能だが、他クラスからの書き込みを巻き込む副作用が大きく、**Testcontainers per-class でコンテキスト分離が正**。

#### 引継ぎ事項 3: cleanup.sql 機構の MySQL 互換性問題

phaseX-9 Step 2/3 で導入した cleanup.sql は **H2 専用構文**（`SET REFERENTIAL_INTEGRITY FALSE/TRUE`）。本番 MySQL では `SET FOREIGN_KEY_CHECKS=0/1` 相当だが構文非互換。Phase 21 で MySQL 移行時に以下のいずれかが必要:

a. cleanup.sql の構文を `SET FOREIGN_KEY_CHECKS` 系に置換（9 ファイル分）
b. `@Sql` ではなく Testcontainers の `withInitScript()` + 各テスト前の Java 側 TRUNCATE で代替
c. cleanup 機構自体を再設計（per-class MySQL なら共有汚染が無いため、各テストで TRUNCATE 不要）

判断は実装フェーズで PoC を経て決定。

#### 引継ぎ事項 4: `scheduler-enabled=true` 13 クラスの非決定性

phaseX-9 Step 4 で `@Sql` 適用しても `@Scheduled` 発火タイミングの非決定性は残る。Phase 21 で以下のいずれかが必要:

a. `@MockitoBean(TaskScheduler.class)` 化して発火を完全制御
b. scheduler を完全分離する設計（テスト用 `TaskScheduler` Bean 差し替え）
c. Testcontainers + per-class で scheduler 影響を受け流す（コンテキスト分離で他クラスへ漏れない）

---

## 着手前提条件

- phaseX-9 全 Step が完了済み（cleanup.sql + `@Sql` 方式 + 週次 random CI ジョブが稼働中）
- `feedback_free_tier_first` メモリに従い、AWS / GitHub Actions 無料枠を超えない範囲で設計する
- ローカル開発で Docker が必須化される（既に必須なので影響小・設計書に明記）
- 既存の H2 ベース 108 件の `@SpringBootTest` クラス全体を一括移行するのか、段階的に移行するのかを実装フェーズで判断
- phaseX-7 メモリ方針「実装と同じフェーズ内で設計書を更新する」に従い、Phase 21 でテスト基盤の設計書（`docs/test_design/` 等の新設も含む）を整備する

---

## スコープ

### スコープ内（本フェーズで扱う）

| # | 項目 | 内容 |
|---|---|---|
| S1 | Testcontainers 採用 PoC | 既存 1 クラス（`FaultInjectionLogRepositoryTest`）を Testcontainers + MySQL で動かし、起動時間 / リソース消費 / 互換性を実機計測 |
| S2 | per-class MySQL 設計 | `@Container` をクラスレベルで宣言する標準パターン / context キャッシュとの両立方針 / `@DynamicPropertySource` で `spring.datasource.url` 差し替え |
| S3 | phaseX-9 引継ぎ 4 クラスの再設計 | テーブル間アーカイブ系 / マルチスレッド検証 を Testcontainers per-class で構造的に分離 |
| S4 | cleanup 機構の再設計 | per-class MySQL では cleanup 不要になる範囲 / 残す範囲を判定し、phaseX-9 の cleanup.sql 9 ファイルを置換または廃止 |
| S5 | H2 ドリフト系の構造的根絶 | 027/037/038/044/045/049/050 の同型再発を CI で検知できる状態にする（本番 MySQL 等価テスト） |
| S6 | CI 時間予算の確認 | GitHub Actions 月 2,000 分（無料枠）の範囲内に収まる構成を PoC で確認し、超えそうなら scope を絞る |
| S7 | 本番マイグレーション戦略との整合 | phaseX-6 の主要テーブル存在確認 / `schema.sql` + `spring.sql.init.mode=always` の運用と Testcontainers の `withInitScript()` をどう共存させるか |

### スコープ外

- Console（PHPUnit + SQLite）/ Market（Vitest）側のテスト DB 移行（Core の H2/MySQL 乖離が起点なので別フェーズで再検討）
- 全テストの再設計（コスト過大、本フェーズの目的は基盤移行）
- 本番 MySQL の DDL リファクタリング（H2 互換性のため残された妥協部分は phaseX-6 の主要テーブル存在確認で事後検知）
- E2E テスト（実機 Selenium / Playwright）の拡充

---

## 設計判断の選択肢（実装案）

実装フェーズで選定する候補。本設計書ではスコープ定義として 3 案を提示するに留める。

### 案 A: Testcontainers per-class MySQL（全テスト一括移行）

- **効果**: 完全分離 + 本番乖離も解消。H2 ドリフト系 7 件を構造的に根絶
- **コスト**: 高（108 クラスへの一括適用 / CI 時間倍増の可能性 / Testcontainers 学習）
- **判断**: ⭐ 本命候補だがコスト次第で B にダウングレード

### 案 B: Testcontainers per-class MySQL（段階的移行）

- **効果**: phaseX-9 引継ぎ 4 クラス + 件数アサーションを伴う Repository テストのみ先行移行。残りは H2 + cleanup.sql のまま
- **コスト**: 中（移行対象を 10〜20 クラスに絞る）
- **判断**: ⭐⭐ コスト対効果が高い。H2 ドリフト系の構造的根絶は不完全（本番乖離の検知は移行対象クラスのみ）

### 案 C: Testcontainers shared MySQL（テスト全体で 1 インスタンス共有）

- **効果**: H2 と同じ「共有 DB + cleanup.sql」モデルを MySQL で再現。本番乖離は解消するが phaseX-9 の妥協点（テスト分離）は引き継ぐ
- **コスト**: 低（テスト時間増分小・既存 cleanup.sql を MySQL 構文に置換するのみ）
- **判断**: 🟡 phaseX-9 の引継ぎ妥協点（4 クラス）を解決しないため位置付けが弱い

### 案の組み合わせ（実装フェーズで具体化）

- **A → 段階的に B**: PoC は A で進めて全体像を把握し、CI 時間予算が厳しければ B にスコープを絞る
- **B + C**: 引継ぎ 4 クラスのみ per-class（A の局所適用）+ 残りは shared MySQL（C）

---

## 実装 Step（暫定）

実装フェーズに移行する場合の暫定段取り。スコープ定義段階では具体化せず、PoC 結果を元に確定させる。

| Step | 内容 | 想定工数 |
|---|---|---|
| 0 | 着手判断材料の収集（CI 時間予算 / Docker サイズ / Testcontainers 学習コスト） | 0.5 日 |
| 1 | PoC：`FaultInjectionLogRepositoryTest` を Testcontainers + MySQL に移行・起動時間 / 互換性 / cleanup 方針を実機計測 | 1〜2 日 |
| 2 | per-class MySQL の標準パターン規約化（`docs/ai_context/test_insights.md` カテゴリ 7-2 への追記） | 0.5 日 |
| 3 | phaseX-9 引継ぎ 4 クラスの再設計（テーブル間アーカイブ / マルチスレッド検証） | 2〜3 日 |
| 4 | 案選定（A 全件 / B 段階 / C shared）と全件適用 | 3〜5 日 |
| 5 | cleanup.sql 機構の置換または廃止（phaseX-9 の 9 ファイルを再評価） | 1 日 |
| 6 | CI 週次 random ジョブの Testcontainers 化（または MySQL 専用ジョブ追加） | 0.5 日 |
| 7 | H2 関連設定の削除 / 規約整理 / 本番マイグレーション戦略との整合確認 | 1 日 |
| **合計** | | **約 9.5〜13.5 日** |

---

## コスト試算

**スコープ定義段階：恒久 $0**
- 本設計書作成のみで AWS / GitHub Actions リソース変更なし

**実装フェーズ移行時の追加コスト**:

| 項目 | 試算 | 注意点 |
|---|---|---|
| GitHub Actions CI 時間 | 現状 `mvn test` 3〜4 分（H2）→ Testcontainers MySQL 起動含めて 8〜15 分？ | per-class だと MySQL コンテナ起動が context 数だけ発生し倍増しうる。PoC で実測必要 |
| ローカル開発 | Docker は既に必須なので影響小 | テスト実行時に Docker daemon 起動が必要・初回 MySQL イメージ pull で数百 MB |
| 学習コスト | Testcontainers 学習 1〜2 日 | `org.testcontainers:mysql` の README + Spring Boot 統合（`@DynamicPropertySource`） |
| MySQL イメージ | `mysql:8.x` の Docker イメージ約 600 MB（CI ランナーキャッシュ可） | GitHub Actions Linux ランナーで毎回 pull するとネットワーク帯域負荷。`actions/cache` で対策 |

**無料枠超過リスク**: 案 A（全件移行）で CI 時間が 20 分超になると、月 4 回（cron）+ 通常 PR で月 100〜200 分。GitHub Actions 月 2,000 分の 10% 程度なので余裕はあるが、**通常 PR で deploy.yml の test-core ジョブ自体を Testcontainers 化するか別ジョブとして並走させるかの判断が必要**。

---

## 完了の定義

### スコープ定義段階（本フェーズ）

- [x] phaseX-9 で残った妥協点が引継ぎ事項として明記されている
- [x] H2 ドリフト系 7 件の出典トラブルが対象として列挙されている
- [x] 抹本対策案 A〜C の比較表が提示されている
- [x] phaseX-9 設計書の「関連ドキュメント」に Phase 21 へのリンクが追加されている（Step 6 で実施）
- [ ] **ユーザー判断**: 実装フェーズ移行可否（メモリ `feedback_free_tier_first` の制約下で着手するか / 技術的負債として記録するか）

### 実装フェーズ完了の定義（着手判断後に確定）

- [ ] Step 1：PoC で Testcontainers + per-class MySQL の起動時間 / リソース消費 / 互換性が計測されている
- [ ] Step 2：per-class MySQL の標準パターンが `test_insights.md` カテゴリ 7-2 に追記されている
- [ ] Step 3：phaseX-9 引継ぎ 4 クラスが Testcontainers per-class で再設計されている
- [ ] Step 4：選定案（A / B / C）が全件適用されている
- [ ] Step 5：cleanup.sql 機構が置換または廃止され、規約が更新されている
- [ ] Step 6：CI ジョブが Testcontainers + MySQL で稼働開始
- [ ] Step 7：H2 関連設定が削除または最小化されている
- [ ] CI 本番デプロイで一連のワークフローが PASS
- [ ] H2 ドリフト系トラブルの再発が CI で構造的に検知される状態になっている（同型バグを意図的に仕込んでも CI で落ちることを確認）

---

## リスクと対応

| リスク | 影響 | 対応 |
|---|---|---|
| Testcontainers の MySQL コンテナ起動が CI で 10 分以上かかる | CI 時間予算破綻 | Step 1 PoC で実測・無料枠を超えそうなら案 B / C へダウングレード |
| ローカル開発で Docker daemon 起動忘れによるテスト失敗が多発 | 開発体験悪化 | `mvn test` 起動時に Docker 接続確認 + 明確なエラーメッセージを出す lint / wrapper script を整備 |
| phaseX-9 で導入した cleanup.sql 9 ファイルが冗長化する | 規約の二重管理 | Step 5 で cleanup.sql の置換・廃止判断を明示し、`test_insights.md` カテゴリ 7-2 を整理 |
| MySQL バージョン差（本番 vs Testcontainers）で別ドリフトが発生 | 別系統の H2 ドリフト類似問題 | 本番 RDS の MySQL バージョン（8.x）と Testcontainers の `mysql:8.0.x` を厳密に揃える / バージョン差分確認テストを Step 1 PoC に含める |
| Testcontainers が CI ランナー OS との相性問題を起こす（rootless docker 等） | CI 一時停止 | GitHub Actions の ubuntu-latest が Testcontainers 公式サポート対象か事前確認 / 必要ならランナーバージョン固定 |

---

## 関連ドキュメント

- 直接の起因（テスト分離問題）: [phaseX-9_test_isolation_redesign.md](phaseX-9_test_isolation_redesign.md)
- 直接の起因（テスト分離問題・実装計画）: [phaseX-9_implementation_plan.md](../../implementation/phaseX-9_implementation_plan.md)
- 引継ぎ妥協点の棚卸し: [phaseX-9_concession_inventory.md](../../implementation/phaseX-9_concession_inventory.md)
- H2 ドリフト系トラブル群: [027](../../troubles/027_workflow_test_h2_schema_and_json_payload.md) / [037](../../troubles/037_flyway_misassumed_phase14_tables_missing.md) / [038](../../troubles/038_products_price_stock_not_null_drift.md) / [044](../../troubles/044_operation_logs_table_missing_users_id_unsigned_drift.md) / [045](../../troubles/045_sales_return_table_missing_users_id_unsigned_drift.md) / [049](../../troubles/049_password_histories_table_missing_in_schema_sql.md) / [050](../../troubles/050_h2_unique_constraint_test_helper_collision.md)
- 同系列のメタ品質フェーズ: [phaseX-6_post_deploy_schema_healthcheck.md](phaseX-6_post_deploy_schema_healthcheck.md)（スキーマ層）/ [phaseX-7_ai_collaboration_antipatterns.md](phaseX-7_ai_collaboration_antipatterns.md)（AI 協働層）/ [phaseX-9_test_isolation_redesign.md](phaseX-9_test_isolation_redesign.md)（テスト分離層）
- 規約追記対象: [test_insights.md](../../ai_context/test_insights.md) カテゴリ 7-2（cleanup.sql 規約 5 項目の MySQL 互換構文への置換）
- 関連 AP: [AP-001](../../ai_context/ai_collaboration_antipatterns.md#ap-001-既存スキーマ既存ddlを読まずに新規追記)（H2/本番乖離による FK 型ドリフト）/ [AP-009](../../ai_context/ai_collaboration_antipatterns.md#ap-009-テスト分離不足--単発-pr-で類似クラス見落とし)（テスト分離不足）
