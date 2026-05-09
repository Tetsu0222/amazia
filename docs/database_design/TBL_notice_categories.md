# テーブル定義書：notice_categories

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | notice_categories |
| 論理名 | お知らせ分類マスタ |
| 所属システム | Core |
| 説明 | フェーズ19 お知らせ機能の分類マスタ。`important`（重要）/ `normal`（普通）の 2 件を schema.sql の `INSERT IGNORE` で初期投入する |
| 追加フェーズ | フェーズ19（r2） |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | 分類ID | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | code | 分類コード | VARCHAR | 20 | NOT NULL | - | UNIQUE。`important` / `normal` |
| 3 | label | 表示名 | VARCHAR | 50 | NOT NULL | - | UI 表示用ラベル（重要 / 普通） |
| 4 | display_order | 表示順 | INT | - | NOT NULL | 0 | 一覧 / プルダウンでの並び順 |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| uk_notice_categories_code | UNIQUE | code |

## 制約

| 制約名 | 種別 | 内容 |
|--------|------|------|
| uk_notice_categories_code | UNIQUE | `code` は分類間で一意 |

## 初期データ

| id | code | label | display_order |
|----|------|-------|--------------|
| 1 | important | 重要 | 1 |
| 2 | normal | 普通 | 2 |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| notices | 1:N | お知らせ本体の分類 |

## 設計上の注意

- 分類 ID は Service / Controller / Console FormRequest からハードコードせず、`amazia.notice.categories.important-id` / `normal-id` を `@Value` または `config('notice.categories.*')` 経由で取得する（規約 4-1）。
- 当面分類は 2 件のみ。将来追加する場合は `display_order` を再採番する運用とし、コード自動採番には依存しない。
- マスタ初期投入は本番 MySQL では schema.sql の `INSERT IGNORE` で冪等性確保。H2 テスト環境では `test-data.sql` で seed する（schema.sql は H2 で読み込まないため）。

## Entity

`com.example.notice.entity.NoticeCategory`

## マイグレーションファイル

- `amazia-core/src/main/resources/schema.sql`（フェーズ19 Step A-1）
