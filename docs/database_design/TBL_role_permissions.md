# テーブル定義書：role_permissions

## 基本情報

| 項目 | 内容 |
|------|------|
| テーブル名 | role_permissions |
| 論理名 | ロール・パーミッション中間テーブル |
| 所属システム | Core |
| 説明 | ロールとパーミッションの多対多の関係を管理する中間テーブル。フェーズ11で追加。 |

## カラム定義

| # | カラム名 | 論理名 | 型 | 長さ | NULL | デフォルト | 備考 |
|---|----------|--------|-----|------|------|------------|------|
| 1 | role_id | ロールID | BIGINT UNSIGNED | - | NOT NULL | - | FK → roles.id |
| 2 | permission_id | パーミッションID | BIGINT UNSIGNED | - | NOT NULL | - | FK → permissions.id |

## インデックス

| インデックス名 | 種別 | カラム |
|----------------|------|--------|
| PRIMARY | PRIMARY KEY | (role_id, permission_id) |
| role_permissions_role_id_fk | INDEX | role_id |
| role_permissions_permission_id_fk | INDEX | permission_id |

## 関連テーブル

| テーブル名 | 関係 | 説明 |
|------------|------|------|
| roles | N:1 | ロール |
| permissions | N:1 | パーミッション |

## 備考

- 複合主キー (role_id, permission_id) により同一ロール・パーミッションの重複登録を防ぐ
- adminロールには全パーミッションが初期データで設定される

## マイグレーションファイル

`src/main/resources/db/migration/V1__create_auth_tables.sql`（amazia-core）
`src/main/resources/db/migration/V2__insert_initial_data.sql`（初期データ）
