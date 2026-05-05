# テーブル定義書：permissions

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | permissions |
| 論理名 | パーミッション |
| 所属システム | Core |
| 説明 | 画面（URL）単位のアクセス権限を管理する。フェーズ11で追加。 |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | id | ID | BIGINT UNSIGNED | - | NOT NULL | AUTO_INCREMENT | PK |
| 2 | screen_id | 画面ID | VARCHAR | 100 | NOT NULL | - | UNIQUE（例：product.list / user.create） |
| 3 | name | パーミッション名 | VARCHAR | 100 | NOT NULL | - | 表示名（例：商品一覧） |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | id |
| permissions_screen_id_unique | UNIQUE | screen_id |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| role_permissions | 1:N | このパーミッションを持つロールの中間テーブル |

## 備考

- パーミッションの粒度は **画面単位（URL単位）**。ボタン単位の制御は行わない
- `screen_id` はドット区切りの階層形式（例：`product.list`、`user.create`）
- adminロールは全パーミッションを保有（初期データで設定）

## 初期データ例

| screen_id | name |
|-----------|------|
| product.list | 商品一覧 |
| product.create | 商品登録 |
| product.edit | 商品編集 |
| sku.manage | SKU管理 |
| user.list | 社員一覧 |
| user.create | 社員登録 |
| user.edit | 社員編集 |

## マイグレーションファイル

`src/main/resources/db/migration/V1__create_auth_tables.sql`（amazia-core）
`src/main/resources/db/migration/V2__insert_initial_data.sql`（初期データ）
