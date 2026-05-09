# 054: フェーズ18 inquiries / inquiry_messages が本番 DB に作成されない（FK 型不一致 / 044 同型）

## ステータス
✅ 解決済（2026-05-09）

## 発症箇所
- 本番 EC2 デプロイ後の GitHub Actions ヘルスチェック（[deploy.yml の「ヘルスチェック - 主要テーブル存在確認」ステップ](../../.github/workflows/deploy.yml)）
- 出力例：
  ```
  ===== 不足テーブル =====
  inquiries
  inquiry_messages
  Error: 主要テーブルが 46 件中 44 件しか存在しない
  Error: Process completed with exit code 1.
  ```

## 症状
- フェーズ18 デプロイ後、`required_tables.txt` に追記した `inquiries` / `inquiry_messages` が本番 MySQL に存在しないと検知され、CD パイプラインが exit 1。
- Spring Boot 起動 / HTTP 200 応答自体は通る（`spring.sql.init.continue-on-error=true` のため DDL 失敗が WARN 化されているだけ）。
- ローカル H2（`InquiryRepositoryTest` / `InquiryServiceTest` 等）では緑だったため、PR ビルド・PR レビュー時には検知できず、本番デプロイで初めて顕在化（044 / 049 と同パターン）。

## 根本原因
`amazia-core/src/main/resources/schema.sql` フェーズ18 セクションの `inquiries.user_id` を **`BIGINT`（UNSIGNED 無し）** で記述してしまったが、参照先の **`market_customers.id` は `BIGINT UNSIGNED`**（[schema.sql §238 phase13](../../amazia-core/src/main/resources/schema.sql)）。

MySQL の FK 制約は **参照元と参照先の列型が完全一致していること**を要求するため、`CREATE TABLE inquiries (...) CONSTRAINT fk_inquiries_user FOREIGN KEY (user_id) REFERENCES market_customers(id)` が型不一致でエラーになる。`spring.sql.init.continue-on-error=true` がエラーを WARN 化して握り潰したため、`inquiries` テーブル自体が作成されず、連動して FK で参照する `inquiry_messages` も意味を成さない（依存先である `inquiries` が無いため、`inquiry_messages.fk_inquiry_messages_inquiry FOREIGN KEY (inquiry_id) REFERENCES inquiries(id)` も同様に失敗）。

> H2 は MySQL ほど厳密に符号付き / 符号無し整数型を区別しないため、ローカルテストではすり抜けた。phase15 が `phase15_delivery_management.md ER §備考` で「`users` / `market_customers` / `address` 等 Laravel 由来 ID 系は `BIGINT_UNSIGNED`、Core オリジナルテーブルは `BIGINT`。FK では型を合わせる必要がある（029 / 037 起因）」と明記していたが、フェーズ18 r1 設計時にこの規約を見落とした。

## なぜ CI で検知できなかったか
1. **本番 MySQL 互換テストが CI に無い**：CI は H2 でのみ単体・統合テストを走らせる構成。MySQL 依存の DDL 互換性は本番デプロイで初めて検証される（044 / 049 と同じ構造）。
2. **`continue-on-error=true` が WARN 化を許容**：DDL 失敗が起動時ログに残るだけでアプリは起動するため、`/actuator/health` レベルの軽量ヘルスチェックでは検知不能。phaseX-6 で導入した `required_tables.txt` ベースの post-deploy ヘルスチェック（044 教訓）が今回の防御線として機能した。
3. **設計書段階のレビューでもすり抜けた**：phase18 設計書 r3 まで誰もこの型不一致に気づかなかった（ただし phase15 ER §備考の警告自体は r1 から存在していた）。

## 修正内容
- `amazia-core/src/main/resources/schema.sql` フェーズ18 §1-1 `inquiries.user_id` を `BIGINT NOT NULL` → **`BIGINT UNSIGNED NOT NULL`** に変更。同セクションに 045 / 044 同型対策のコメントを追記し、後続フェーズで同型のミスが発生しないようにした。
- `docs/database_design/TBL_inquiries.md` の `user_id` カラム型表記を `BIGINT` → `BIGINT UNSIGNED` に修正。注記で「market_customers.id が BIGINT UNSIGNED のため型を一致させる必要あり」と明示。
- 既存の `Inquiry.java` Entity 側は `Long userId` のままで対応可能（Hibernate は MySQL `BIGINT UNSIGNED` を Java `Long` にマッピングできる。phase17 `NotificationSubscription.userId : Long` と同方針）。

## 本番 DB への再適用手順（ユーザー側）
1. `main` に修正をマージ → GitHub Actions が deploy.yml を起動
2. EC2 で Spring Boot が再起動し、`spring.sql.init.mode=always` で schema.sql が再実行される
3. `CREATE TABLE IF NOT EXISTS inquiries (...)` が今度は型一致で成功 → `inquiries` 作成
4. `CREATE TABLE IF NOT EXISTS inquiry_messages (...)` も連動して成功
5. `INSERT IGNORE INTO notification_subscriptions ... 'inquiry_alerts' ...` で自動購読が投入される
6. post-deploy ヘルスチェック「主要テーブル存在確認」が 46/46 で緑になる

> `CREATE TABLE IF NOT EXISTS` は冪等。既に何かしら作られていてもエラーにならない（ただし今回は前回 DDL が完全失敗してテーブルが無い状態のはずなので、新規作成が走る）。万一中途半端な状態が残っていた場合、ユーザー側で `SHOW CREATE TABLE inquiries;` を確認の上、手動 `DROP TABLE inquiries, inquiry_messages;` してから再デプロイする。

## 再発防止
| 観点 | 対策 |
|------|------|
| 設計書 / 実装計画書 | フェーズ18 設計書 §3.1 / 実装計画書 §2-1-1 のスキーマ定義に **「FK 参照先の型と一致させる（044 / 045 教訓）」** の注記を追加（次回改訂時 / 本トラブル対応で TBL_inquiries.md に注記済） |
| schema.sql | フェーズ18 セクション冒頭コメントに 045 同型対策の説明を追記済（schema.sql §811 付近） |
| AI 実装フロー | 新規テーブルの FK 列型を決める際に、参照先テーブルの型を必ず `grep -nE "CREATE TABLE.*<参照先>"` で確認する手順を ai_collaboration_antipatterns.md に追加（AP-NNN として後述） |
| CI | MySQL 互換性テストの CI 組込は phaseX-N（テスト基盤整備）で別途検討。t3.micro の制約上 docker mysql を CI で起動するのはコスト高のため、`schema.sql` の Lint（FK 列型の参照先一致チェッカー）を作るのが現実的 |
| ヘルスチェック | phaseX-6 の `required_tables.txt` チェッカーが今回の防御線として機能した（044 教訓の効果が出た）。継続して維持 |

## AI協働観点
- AI の判断ミス：**フェーズ18 Step 1 で schema.sql を書いた際、`market_customers.id` の型を確認せず `inquiries.user_id BIGINT NOT NULL` と書いてしまった**。設計書には「`fk_inquiries_user FOREIGN KEY (user_id) REFERENCES market_customers(id)`」と書かれていたが、参照先の型を確認するステップが抜けた。phase15 ER §備考の警告（029 / 037 起因）も読んでいなかった。
- 人間が止めるべきだった点：phase18 設計書 r1〜r3 のレビュー段階で、誰もこの型不一致に気づかなかった。設計書段階での「FK 列型 = 参照先列型」の機械的チェックがあれば防げた。
- 該当アンチパターン：[ai_collaboration_antipatterns.md AP-001 系（スキーマ追加時の既存型確認漏れ）](../ai_context/ai_collaboration_antipatterns.md) の派生。新規 AP として「FK 列型と参照先型の整合性確認漏れ」を追加検討。

## 関連トラブル
- [044: schema.sql DDL 失敗が continue-on-error=true で WARN 化される問題（phaseX-6 の起源）](044_*.md)（本不具合の親パターン）
- [049: password_histories テーブルが schema.sql に未追記](049_password_histories_table_missing_in_schema_sql.md)（044 同型・テーブル不存在の別ケース）
