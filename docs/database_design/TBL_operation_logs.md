# テーブル定義書：operation_logs

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | operation_logs |
| 論理名 | 操作履歴 |
| 所属システム | Core |
| 説明 | Console 画面・API での操作記録を保持する監査ログ。誰が・いつ・何を・どこから操作したかを追跡する |
| 追加フェーズ | フェーズ14（V6 base + V10 で screen_name / api_name 追加） |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | ログID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | user_id | 操作者ID | BIGINT | - | NOT NULL | - | FK: users.id（Console 社員） |
| 3 | action | 操作種別 | VARCHAR | 100 | NOT NULL | - | CREATE / UPDATE / DELETE / APPROVE 等のコード |
| 4 | target_type | 対象種別 | VARCHAR | 50 | NULL | NULL | product / sku / sales 等 |
| 5 | target_id | 対象ID | BIGINT | - | NULL | NULL | target_type に対応する PK |
| 6 | screen_name | 画面名 | VARCHAR | 100 | NULL | NULL | P14 追加。操作元の画面（Console UI） |
| 7 | api_name | API名 | VARCHAR | 100 | NULL | NULL | P14 追加。操作経由の Core API 名 |
| 8 | comment | コメント | TEXT | - | NULL | NULL | 補足説明 |
| 9 | created_at | 作成日時 | DATETIME | - | NOT NULL | CURRENT_TIMESTAMP | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| idx_operation_logs_user_id | INDEX | user_id |
| idx_operation_logs_action | INDEX | action |
| idx_operation_logs_target | INDEX | (target_type, target_id) |
| idx_operation_logs_created_at | INDEX | created_at |
| idx_operation_logs_screen_name | INDEX | screen_name |
| idx_operation_logs_api_name | INDEX | api_name |

## 外部キー

| FK名 | カラム | 参照先 |
|------|--------|--------|
| fk_operation_logs_user | user_id | users(id) |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| users | N:1 | 操作した Console 社員 |

## 設計上の注意

- `user_id` は Console 社員 `users.id` を参照する（Market 顧客 `market_customers` ではない）。Market 側の操作はこのテーブルでは記録しない。
- `screen_name` / `api_name` は P14 の V10 で追加されたカラム。画面起点の操作は `screen_name` を、API 直叩きの操作は `api_name` を入れる運用。両方入れると「どの画面のどの API を呼んだか」が追える。
- インデックスは検索ユースケース別に多めに張っている（社員別・操作種別・対象別・期間別・画面別・API別）。書き込み量とのトレードオフを将来見直す可能性あり。
- 命名規則・出力フォーマットは `docs/ai_context/operation_logs_naming.md` を参照。

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ14 / V6 + V10 相当：screen_name / api_name 含む完全版）
- `amazia-core/src/main/resources/db/migration/V6_*.sql` / `V10_*.sql`（名残ファイル：本番では実行されない）
