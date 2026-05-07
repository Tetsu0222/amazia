# 049: password_histories テーブルが schema.sql 未記載のまま本番に存在せず（044・045 同型・phaseX-6 ヘルスチェックで事前検知）

## ステータス
✅ 解決済（2026-05-07）

## 発症箇所
- DDL: [amazia-core/src/main/resources/schema.sql](../../amazia-core/src/main/resources/schema.sql)
- Entity: [PasswordHistory.java](../../amazia-core/src/main/java/com/example/auth/entity/PasswordHistory.java) (`@Table(name = "password_histories")`)
- 設計書: [TBL_password_histories.md](../database_design/TBL_password_histories.md)（「本番 schema.sql には未記載（要検討事項）」と既知扱いだった）

## 症状
phaseX-6 の主要テーブル存在確認ヘルスチェックが [048](048_cd_healthcheck_sql_quote_break_inside_sh_c.md) の派生バグまで直り **本来の役割で初動した瞬間**、以下の不足検知で `exit 1`:

```
期待: 33 件 / 実測: 32 件
===== 不足テーブル =====
password_histories
Error: 主要テーブルが 33 件中 32 件しか存在しない
```

呼び出されていれば社員のパスワード変更画面で 1146 (`Table 'amazia.password_histories' doesn't exist`) → 500 となるサイレント故障。デプロイ後の自然発見ではなく、ヘルスチェックが事前検知した。

## 根本原因
本プロジェクトは Flyway 未使用で `schema.sql` 方式（[037](037_flyway_misassumed_phase14_tables_missing.md) 参照）。`password_histories` の DDL は Flyway 名残ファイル `db/migration/V1__create_auth_tables.sql` にしか書かれておらず、本番起動時には **誰もこのテーブルを作らない**。

設計書 [TBL_password_histories.md](../database_design/TBL_password_histories.md) には phaseX-6 以前から「本番 schema.sql には未記載（要検討事項）」と明記されていたが、フェーズ完了基準に「設計書の『要検討事項』を消化済みか」が無く、放置されたまま運用に入っていた。

044・045 と同じ「H2 / 本番 MySQL ドリフト + `continue-on-error` で潰された DDL 失敗」系統だが、本件は DDL 失敗ですらなく **DDL が schema.sql に存在しない** という更に静かな型のドリフト。

## なぜ CI で検知できなかったか
- H2 テスト環境では JPA `ddl-auto=create-drop` が Entity 定義から自動生成するためテーブルが必ず存在する
- 本番 MySQL では schema.sql 起動時実行のみで作成され、schema.sql に書いていなければ作られない
- 044・045 の「DDL が `continue-on-error` で潰される」型と異なり、本件は DDL 自体が無いため WARN すら出ない（より静かな型）
- 設計書の「要検討事項」表記がフェーズ完了チェックに連動していなかった

## 修正内容
[schema.sql](../../amazia-core/src/main/resources/schema.sql) の `password_reset_tokens` の直後に `password_histories` の `CREATE TABLE IF NOT EXISTS` を追記。FK 列 `user_id` は `users.id` の `BIGINT UNSIGNED` に合わせる（044・045 同型のドリフト回避）。

```sql
CREATE TABLE IF NOT EXISTS password_histories (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT UNSIGNED NOT NULL,
    password_hash VARCHAR(255)    NOT NULL,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_password_histories_user (user_id),
    CONSTRAINT fk_password_histories_user FOREIGN KEY (user_id) REFERENCES users(id)
);
```

[TBL_password_histories.md](../database_design/TBL_password_histories.md) のカラム表（`BIGINT UNSIGNED`）・インデックス節（`idx_password_histories_user`）・FK 節（`fk_password_histories_user`）・マイグレーションファイル節（schema.sql 追記済み）・設計上の注意（解消経緯）を更新。

## 再発防止
| 観点 | 対策 |
|------|------|
| 設計書「要検討事項」の漏れ | フェーズ完了チェックに **「該当フェーズで触ったテーブルの TBL_*.md に未消化の『要検討事項』『未記載』が残っていないか」** を含める。`docs/ai_context/test_insights.md` カテゴリ7-2「設計観点」に追加 |
| 同型棚卸しの徹底 | 044・045 修正時に「同じ参照先（users.id）を持つ全 FK 列の棚卸し」は実施したが、**「同じ参照先を持つテーブルが schema.sql に存在するかどうか」までは確認していなかった**。次回 schema.sql 編集時は `@Table` アノテーション全件 grep → schema.sql `CREATE TABLE` 一覧との突合せを実施 |
| ヘルスチェックの実成果評価 | 本件は phaseX-6 ヘルスチェックの **初の「044/045 同型不具合の事前検知」事例**。デプロイ後の自然発見ではなく CD で止められた（045 までは本番運用後に検知）。phaseX-6 の効用が定量的に確認できたケースとして次回フェーズ振り返りで言及 |

## 関連トラブル
- [044](044_operation_logs_table_missing_users_id_unsigned_drift.md) / [045](045_sales_return_table_missing_users_id_unsigned_drift.md): 同型（DDL は書いたが UNSIGNED 不一致で `continue-on-error` に潰された）
- [037](037_flyway_misassumed_phase14_tables_missing.md): 本プロジェクトが Flyway 未使用で `db/migration/V*.sql` は死ファイルである旨の判定。本件の DDL が V1 にしか無く本番未適用だった構造的原因
- [046](046_cd_healthcheck_mysql_root_password_unexpanded.md) / [048](048_cd_healthcheck_sql_quote_break_inside_sh_c.md): phaseX-6 ヘルスチェック初動で連続的に踏んだ実装バグ。これらが解消されてようやく本件が検知できた
