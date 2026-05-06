# テーブル定義書：shipping_statuses

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | shipping_statuses |
| 論理名 | 配送ステータスマスタ |
| 所属システム | Core |
| 説明 | 売上レコードの配送状態を表すマスタ。注文確定時は PENDING、出荷後・配達後・返品時に遷移する |
| 追加フェーズ | フェーズ14（V9 相当でステータス追加） |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | ステータスID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | code | ステータスコード | VARCHAR | 50 | NOT NULL | - | UNIQUE。`PENDING` / `SHIPPED` 等 |
| 3 | name | 表示名 | VARCHAR | 100 | NOT NULL | - | UI 表示用の日本語名 |
| 4 | description | 説明 | VARCHAR | 255 | NULL | NULL | 補足説明（運用範囲・将来用途など） |
| 5 | created_at | 作成日時 | DATETIME | - | NOT NULL | CURRENT_TIMESTAMP | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| code | UNIQUE | code |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| sales | 1:N | 注文の配送状態として参照される |

## 初期データ

schema.sql で `INSERT IGNORE` により8件投入する。

| id | code | name | description |
|----|------|------|-------------|
| 1 | PENDING | 配送準備中 | 注文確定後・出荷前 |
| 2 | SHIPPED | 配送済 | 発送完了 |
| 3 | DELIVERED | 配送完了 | 配達完了 |
| 4 | RETURN_REQUESTED | 返品申請中 | 返品申請を受付中 |
| 5 | RETURNED | 返品完了 | 返品処理完了 |
| 6 | CANCELED | 発送前キャンセル | 将来 phase21 |
| 7 | DELIVERY_FAILED | 配達失敗 | 将来 phase21 |
| 8 | RESCHEDULED | 再配達手配中 | 将来 phase21 |

## ステータス遷移（フェーズ14時点）

```
PENDING ──→ SHIPPED ──→ DELIVERED
                          │
                          └──→ RETURN_REQUESTED ──→ RETURNED
```

- 6〜8（CANCELED / DELIVERY_FAILED / RESCHEDULED）はマスタとしては定義済みだが、フェーズ14時点では運用しない。フェーズ21（抽選機能・補助機能）で活用予定。

## 設計上の注意

- レコードは Console 画面からの登録対象ではなく、schema.sql 起動時のシード投入のみで管理する。
- `code` は ENUM 化せず VARCHAR + UNIQUE で管理する方針（マスタ拡張を schema.sql で完結させるため）。
- `sales.shipping_status_id` の初期値は ID=1（PENDING）。注文確定 API は明示的に1を設定する。

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ14 / V6 + V9 相当：8ステータスを含む完全版）
