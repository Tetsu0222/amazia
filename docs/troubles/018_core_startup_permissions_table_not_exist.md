# 018: amazia-core 起動失敗 — permissions テーブルが存在しない

## ステータス
✅ 解決済（2026-05-05）

## 発症箇所
amazia-core コンテナ（`docker compose -f docker-compose.local.yml up`）

## 症状
amazia-core が Restarting ループに入り起動できない。ログに以下のエラー：
```
Caused by: java.sql.SQLSyntaxErrorException: Table 'amazia.permissions' doesn't exist
Failed to execute SQL script statement #3 of data.sql: INSERT IGNORE INTO permissions ...
```

## 根本原因
3つの問題が重なった：

1. **`permissions`・`role_permissions` テーブルが存在しない**
   - テーブル定義は `V1__create_auth_tables.sql`（Flyway）にのみあり、Flyway を使用していないため未実行
   - Spring JPA の `ddl-auto=update` でも `Permission`/`RolePermission` Entity が存在しないため自動生成されない

2. **`data.sql` が JPA DDL より先に実行される**
   - `spring.jpa.defer-datasource-initialization=true` が `application-local.properties` にのみ設定されていたが、`application.properties`（共通）に設定がないと Spring Boot 3.x で確実に機能しない

3. **`users.id` が `bigint unsigned`（Laravel 由来）で FK テーブルと型不一致**
   - Laravel の migration で `users` テーブルが先に作られており、`id` が `bigint unsigned`
   - JPA が追加する FK（`user_id bigint`）と型不一致になり WARN が発生

## なぜ CI で検知できなかったか
- テスト環境（H2 インメモリ）では `permissions` テーブルも H2 用 data.sql で作成されるため問題が発生しなかった
- Docker 環境での初回起動シナリオがテストされていなかった

## 修正内容

1. **`Permission`・`RolePermission` Entity を新規作成**
   - `src/main/java/com/example/auth/entity/Permission.java`
   - `src/main/java/com/example/auth/entity/RolePermission.java`

2. **`application.properties` に `spring.jpa.defer-datasource-initialization=true` を追加**（共通設定）

3. **MySQL に直接 permissions・role_permissions テーブルを作成**（既存DBの場合）

4. **`ddl-auto=update` を `none` に変更**（`application-local.properties`・`application-dev.properties`）
   - テーブルは既存のため JPA 自動 DDL 不要
   - Laravel 由来の `unsigned` 型との FK 不一致 WARN を解消

5. **FK テーブルの `user_id` を `BIGINT UNSIGNED` に変更**
   - `password_histories`・`password_reset_tokens`・`refresh_tokens`

## 再発防止
| 観点 | 対策 |
|------|------|
| Entity 不在 | テーブル定義 SQL に対応する JPA Entity を必ずセットで作成する |
| data.sql 実行順 | `spring.jpa.defer-datasource-initialization=true` は `application.properties`（共通）に記述する |
| DB混在 | Laravel と Spring JPA が同じ DB を共有する場合、型（unsigned vs signed）の不一致に注意 |
| Docker 初回起動テスト | フェーズ完了時に `docker compose down -v && docker compose up --build` で DB 初期化から確認する |
