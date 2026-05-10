# テーブル定義書：shipping_lead_times

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | shipping_lead_times |
| 論理名 | 都道府県別リードタイムマスタ |
| 所属システム | Core |
| 説明 | 配送方法 × 都道府県（`address.prefecture` と厳密一致）でリードタイム日数を保持するマスタ。`DeliveryScheduleService.calculate(...)` から参照される |
| 追加フェーズ | フェーズX-5 |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | リードタイムID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | shipping_method_id | 配送方法ID | BIGINT | - | NOT NULL | - | `shipping_methods.id` への FK |
| 3 | prefecture | 都道府県名 | VARCHAR | 20 | NOT NULL | - | `address.prefecture` と厳密一致する文字列（"東京都" / "北海道" 等） |
| 4 | lead_time_days | リードタイム日数 | INT | - | NOT NULL | - | 0 以上（0 は無効化扱い） |
| 5 | created_at | 作成日時 | DATETIME | - | NOT NULL | - | |
| 6 | updated_at | 更新日時 | DATETIME | - | NOT NULL | - | |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| uk_shipping_lead_times_method_pref | UNIQUE | (shipping_method_id, prefecture) |
| idx_shipping_lead_times_method_id | INDEX | shipping_method_id |

## 制約

| 名前 | 種類 | 内容 |
|------|------|------|
| fk_shipping_lead_times_method | FK | `shipping_method_id` → `shipping_methods(id)` |
| chk_shipping_lead_times_days_nonneg | CHECK | `lead_time_days >= 0` |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| shipping_methods | N:1 | リードタイムは配送方法ごとに保持 |
| address | 参照のみ（厳密一致） | `address.prefecture` と一致したときのみマスタ値を採用 |

## 初期データ

schema.sql で `INSERT IGNORE` により 47 都道府県 × 3 配送方法 = 141 行を投入する。

**標準値**：
- home_delivery（id=1）= 3 日
- konbini_pickup（id=2）= 4 日
- dropoff（id=3）= 2 日

**離島加算 +2 日（厳格 4 県）**：北海道 / 長崎県 / 鹿児島県 / 沖縄県

| 都道府県 | home_delivery | konbini_pickup | dropoff |
|----------|---------------|----------------|---------|
| 北海道 | 5 | 6 | 4 |
| 長崎県 | 5 | 6 | 4 |
| 鹿児島県 | 5 | 6 | 4 |
| 沖縄県 | 5 | 6 | 4 |
| その他 43 都府県 | 3 | 4 | 2 |

## 設計上の注意

- マスタに該当行がない／`address.prefecture` が NULL or 空文字／文字列が厳密不一致（例："東京" vs "東京都"）の場合は、`application.properties` の `amazia.delivery.lead-time-days.*` 全国一律値にフォールバックする（`DeliveryScheduleService` で防御的に実装）。
- 物理削除は許容しない。マスタを無効化する場合は `lead_time_days = 0` で運用する（このとき `DeliveryScheduleService` は当該マスタ値（0 日）を採用するので注文当日扱いとなる）。
- 文字列正規化（"東京" を "東京都" に補正等）は行わない。Address 入力時のバリデーション側で吸収する想定。
- パフォーマンス：固定 141 行のため第一実装ではキャッシュなし。将来の必要性に応じて `@Cacheable` 化を検討する。

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズX-5）
