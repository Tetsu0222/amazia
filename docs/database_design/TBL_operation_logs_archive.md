# テーブル定義書：operation_logs_archive

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | operation_logs_archive |
| 論理名 | 操作履歴アーカイブ |
| 所属システム | Core |
| 説明 | `OperationLogArchiveJob`（フェーズ17 Step 4-4）が `operation_logs` から 1 年超のレコードを移送する保管先。元 `id` を保持し、INSERT → DELETE を 1 トランザクションで実施する |
| 追加フェーズ | フェーズ17 Step 4-4 |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | ログID | BIGINT | - | NOT NULL | - | PK。元 `operation_logs.id` を引き継ぐ（IDENTITY ではない） |
| 2 | user_id | 操作者ID | BIGINT UNSIGNED | - | NOT NULL | - | 元レコードの `user_id` を保持（FK は張らない） |
| 3 | action | 操作種別 | VARCHAR | 100 | NOT NULL | - | |
| 4 | target_type | 対象種別 | VARCHAR | 50 | NULL | NULL | |
| 5 | target_id | 対象ID | BIGINT | - | NULL | NULL | |
| 6 | screen_name | 画面名 | VARCHAR | 100 | NULL | NULL | |
| 7 | api_name | API名 | VARCHAR | 100 | NULL | NULL | |
| 8 | comment | コメント | TEXT | - | NULL | NULL | |
| 9 | created_at | 元作成日時 | DATETIME | - | NOT NULL | - | 元レコードの `created_at` を保持 |
| 10 | archived_at | アーカイブ実行日時 | DATETIME | - | NOT NULL | - | 本ジョブ実行時刻 |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| idx_ola_created_at | INDEX | created_at |

## 外部キー

なし（運用上の取り回しを優先し、FK は張らない）。

## 設計上の注意

- t3.micro のディスク圧迫を抑えるため、インデックスは PK ＋ `created_at` の 1 本のみ。
- `id` を引き継ぐことで、運用調査時に「移送元 id」と紐付けやすい。
- 本テーブルへの書き込みはバッチ専用。アプリケーションコードからの直接 INSERT は想定しない。

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ17 Step 1-8）
