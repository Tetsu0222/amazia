# 037: Flyway 利用と誤認しフェーズ14 テーブルが本番 DB に作成されていなかった

## ステータス
✅ 解決済（2026-05-06）

## 発症箇所
- 環境: ローカル本番（docker-compose.local.yml の amazia-core / mysql）
- API: `POST /api/customer/orders/confirm`
- 影響テーブル: `payment_methods` / `address` / `sales` / `sales_return` / `shipping_statuses` / `operation_logs`

## 症状
Market から「注文を確定する」を押すと 500 Internal Server Error。Core ログに以下：

```
Caused by: java.sql.SQLSyntaxErrorException: Table 'amazia.payment_methods' doesn't exist
```

`POST /api/customer/orders/confirm` が PaymentMethodRepository.findById(...) を実行した時点で、参照すべき `payment_methods` テーブルが本番 MySQL に存在しないため失敗。フェーズ14 Step B で実装した注文確定機能が本番では一切動作しない状態だった。

## 根本原因
本プロジェクトは **Flyway を使っていない**にもかかわらず、フェーズ14 Step 0 / Step A で **Flyway 利用と誤認したまま** `db/migration/V6_*.sql 〜 V11_*.sql` を作成していた。これらの SQL ファイルは起動時に何も実行されず、フェーズ14 で必要なテーブル群がローカル本番 DB に作成されていなかった。

### プロジェクトの実態（誤認したもの／正しい姿）

| 観点 | 誤認 | 実態 |
|------|------|------|
| マイグレーション機構 | Flyway を使っており `db/migration/V*.sql` が起動時に流れる | **Flyway 依存は pom.xml に無い**。`db/migration/` ディレクトリは過去の名残（飾り） |
| 本番 DB 初期化方式 | Flyway による履歴管理 | `schema.sql` を `spring.sql.init.mode=always` で起動時に毎回流す。**冪等性は `CREATE TABLE IF NOT EXISTS` / `continue-on-error=true` で担保** |
| テスト DB 初期化方式 | Flyway 履歴の検証 | `application-test.properties` で `spring.sql.init.schema-locations=` を空にし、`ddl-auto=create-drop` で **JPA Entity からテーブル自動生成** |
| 既存 V1〜V5 の役割 | Flyway 履歴の正本 | **死ファイル**。同内容が `schema.sql` に冪等版で書かれており、起動時に動くのは schema.sql のみ |

### 誤認に至った経路
1. Step 0-5 着手時に `amazia-core/src/main/resources/db/migration/` の存在を確認
2. V1〜V5 のファイルがあるのを見て「フェーズ13 までは Flyway 管理されている」と早合点
3. **`pom.xml` に flyway 依存があるか・`schema.sql` の運用実態がどうなっているかを確認しないまま** 設計書 r4 と実装計画 md に「Flyway で管理」と記述
4. V6〜V11 を作成し `mvn test` がグリーンになったことで「動いている」と判断
5. しかしテスト環境は H2 + `ddl-auto=create-drop` で **JPA Entity からテーブルが自動生成されるため**、Flyway が動いていなくても通る構成だった

## なぜ CI で検知できなかったか
- `mvn test` のテスト環境は H2 で `ddl-auto=create-drop`。**Flyway / schema.sql どちらも読み込まれず**、JPA Entity（`@Table(name=...)`）からテーブルが自動生成される。そのため Step 0/A の Entity 作成は H2 上では成功するが、本番 MySQL には何も反映されない。
- テスト環境と本番環境の DB 初期化方式が**根本的に異なる**ため、テスト緑＝本番動作 とは言えない構成だった（[027](027_workflow_test_h2_schema_and_json_payload.md) と同じ系統の問題が再発）。
- 本番起動時に「`db/migration/V6〜V11` が `db/migration/` にあるのに無視されている」ことに気付くログ（Flyway は読まれない、`spring.sql.init` も migration ディレクトリは見ない）も出ない。**サイレントに死ファイル化**していた。

## 035 からの再発パターン
035（Market 購入ボタンの sku_id=undefined）で「**Core 側 DTO の getter 名を外挿せず実ファイルを直接読む**」教訓を抽出していた。にもかかわらず本件では「**`db/migration/` ディレクトリがある＝ Flyway 利用**」と pom.xml / 起動ログ / schema.sql を直接読まずに外挿した。**035 と完全に同型の "外挿による誤認" の再発**。

## 修正内容
1. **schema.sql に V6〜V11 相当の冪等版を追記**
   - `payment_methods` / `address` / `shipping_statuses` / `sales` / `sales_return` / `operation_logs` を `CREATE TABLE IF NOT EXISTS` で追加
   - `payment_methods` / `shipping_statuses` の初期データを `INSERT IGNORE` で投入
   - `product_sku_stock_transactions` の拡張カラム（`reference_type` / `reference_id` / `created_by_user_id` / `comment`）と関連 INDEX を `ALTER TABLE` で追加（`continue-on-error=true` により再起動時の重複実行は無視される）
2. **`db/migration/V6〜V11.sql` を削除**（混乱の元になるため死ファイルを残さない方針）
3. **Core コンテナ再起動**で `schema.sql` を再実行し、テーブル作成を反映（ユーザー側で対応）

修正対象ファイル：
- [schema.sql](../../amazia-core/src/main/resources/schema.sql) に **「フェーズ14: 購入機能」セクションを追記**
- 削除：`amazia-core/src/main/resources/db/migration/V6_*.sql 〜 V11_*.sql`（6 ファイル）

## 再発防止
| 観点 | 対策 |
|------|------|
| **マイグレーション機構の確定**（最重要） | 新フェーズ着手時に **`pom.xml` の依存関係 + 起動時ログ** を一度確認し、Flyway / Liquibase / `schema.sql` のいずれが動いているかを **直接観測**する。`db/migration/` ディレクトリの存在は何の証拠にもならない。 |
| 設計書の前提検証 | 設計書 r4 で「Core Flyway で管理」と書いた段階で、その前提が pom.xml と整合しているか裏取りすべきだった。設計書の前提セクションは **コード/設定に対する claim** なので、書く時点でセルフレビューする習慣を `docs/ai_context/operational_insights.md` に追記する。 |
| H2 vs MySQL の挙動差を意識 | `mvn test` がグリーンでも本番で動くとは限らない。Step 完了確認に **ローカル本番（docker-compose.local.yml）での実際の API 叩き** を含める。Step 0/A 段階で `curl http://localhost:8080/api/customer/orders/confirm` 相当の呼び出しまで通せば本件は防げた。 |
| 035 で得た教訓の徹底 | **「外挿せず実ファイルを直接読む」**を、新規ファイル作成時だけでなく **「環境/設定の前提」を立てる時** にも適用する。具体的には、設計書に何かを「前提」として書いた瞬間、それを裏付ける実コード/設定ファイルを 1 つ以上引用する。 |
| 037 自体のメタ的価値 | 035 → 037 で「外挿パターン」が **異なるレイヤーで再発**した（前者は DTO、後者は環境設定）。これは「個別バグへの対症療法では同型再発が止まらない」ことの証左。`docs/ai_context/operational_insights.md` の「カテゴリ3：既存実装棚卸し」を **「カテゴリ3：既存実装と環境設定の棚卸し」** に拡張するのが妥当。 |

## 後日メタ評価タスク

> 035 / 037 の「外挿による誤認」パターンが今後も発生するか、`operational_insights.md` の対策追加で実際に止まったかを評価する。

- [ ] `operational_insights.md` カテゴリ3 を「既存実装と環境設定の棚卸し」に拡張
- [ ] 設計書の「前提」セクションに **裏付け参照ファイル** を必須項目として明記する規約を CLAUDE.md に追加検討
- [ ] phase15 以降の最初のフェーズ着手時に「Flyway / schema.sql どちらが動いているか確認した」のメタログを残し、対策の効果測定材料にする

## 参考
- 関連トラブル: [027](027_workflow_test_h2_schema_and_json_payload.md)（H2 vs MySQL の挙動差で同根）/ [035](035_market_checkout_sku_id_undefined.md)（"外挿による誤認" の前回ケース）
- 関連設計書: [phase14_shipping.md](../design/phase11_20/phase14_shipping.md)（r4 改訂時に「Flyway で管理」と記述したが、本件で **schema.sql 方式** が実態と判明）
- 関連実装計画: [phase14_implementation_plan.md](../implementation/phase14_implementation_plan.md)（Step 0-5 / 0-7 / Step A 全体の前提を `Flyway → schema.sql` に読み替える必要あり）
