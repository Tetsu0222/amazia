# テーブル定義書：warehouses

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | warehouses |
| 論理名 | 倉庫マスタ |
| 所属システム | Core |
| 説明 | 入荷・在庫テーブルから FK 参照される倉庫マスタ。並行運用期はダミー1行（id=1 'default'）のみで運用する |
| 追加フェーズ | フェーズ15（r5 / RRR-3） |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 倉庫ID | BIGINT | - | NOT NULL | - | PK（AUTO_INCREMENT は使わず明示指定） |
| 2 | name | 倉庫名 | VARCHAR | 100 | NOT NULL | - | 全社単一倉庫期は `default` |
| 3 | description | 説明 | VARCHAR | 255 | NULL | NULL | 補足情報 |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| inventories | 1:N | 商品×倉庫の在庫 |
| inbounds | 1:N | 商品入荷ヘッダ |

## 初期データ

schema.sql で `INSERT IGNORE` により以下を投入する。

| id | name | description |
|----|------|-------------|
| 1 | default | 全社単一倉庫 |

## 設計上の注意

- 並行運用期（phase15 r5）は **ダミー1行のみ**。`inventories.warehouse_id` / `inbounds.warehouse_id` の `NOT NULL DEFAULT 1` で NULL UNIQUE 一意性破綻を回避（RRR-3）。
- `warehouses` のレコード数が2行以上になった時点で Console 入荷登録 UI に倉庫選択フィールドを追加する（RRRR-5 / phase15 r5 では UI 非表示）。
- ID 値は `application.properties > amazia.delivery.default-warehouse-id` および Console `config('delivery.default_warehouse_id')` と整合させる。

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ15 / r5）
