# 027: フェーズ12 ワークフロー導入で CI（amazia-core テスト）が全滅

## ステータス
✅ 解決済（2026-05-05）

## 発症箇所
GitHub Actions `Deploy to EC2` ワークフロー
- `amazia-core テスト` ジョブ（[`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml) L16-27）
- `mvn clean test` 実行時に `WorkflowApplyTest` 4ケースが連鎖失敗し、`ApplicationContext failure threshold (1) exceeded` を吐いてパイプラインが停止。

## 症状

`フェーズ12ワークフロー対応` コミット（d44d46f5）以降の CI で、`WorkflowApplyTest` の4テスト全件が以下のエラーで失敗。

```
java.lang.IllegalStateException: ApplicationContext failure threshold (1) exceeded:
  skipping repeated attempt to load context for ...WorkflowApplyTest...
```

ローカルでも `mvn test -Dtest=WorkflowApplyTest` で同じく再現。

## 根本原因

問題は **2 段** あった。1 段目を修正すると 2 段目が露出した。

### ① schema.sql の MySQL 専用構文が H2 で解釈不能

`amazia-core/src/main/resources/schema.sql` に冪等な「既存環境への補完」スクリプトを追加したが、`CREATE TABLE` 内に MySQL 拡張の `INDEX ...` 句をインライン記述していた。

```sql
CREATE TABLE IF NOT EXISTS workflow_requests (
    ...
    INDEX idx_workflow_status (status),       -- ← H2 ではカラム定義として解釈される
    INDEX idx_workflow_target (target_type, target_id),
    INDEX idx_workflow_requester (requested_by)
);
```

H2 はインライン `INDEX` 句を認識せず、`IDX_WORKFLOW_STATUS` を「データ型不明」として弾く:

```
org.h2.jdbc.JdbcSQLNonTransientException: 不明なデータ型: "IDX_WORKFLOW_STATUS"
```

`application-test.properties` は `spring.sql.init.mode=always` だが `schema-locations` を未指定だったため、デフォルトで `classpath:schema.sql` が拾われ、H2 上で実行されてエラー → ApplicationContext のロード自体が失敗。`@SpringBootTest` の閾値（1回目の失敗で打ち切り）に達して 2 件目以降が `skipping repeated attempt` で機械的に Fail 扱いになる。

### ② `WorkflowRequest.payload` の `columnDefinition = "JSON"` で文字列が二重エスケープ保存される

①を修正してテストが進んだ段階で `Status expected:<200> but was:<422>` が新たに発覚。レスポンスは `{"error":"Unprocessable Entity","message":"Invalid payload JSON","status":422}`。

`WorkflowRequest` エンティティでフィールドは `String` 型なのに `columnDefinition = "JSON"` を指定していたため、Hibernate が H2 の JSON 型カラムへ `String` を渡す際に **JSON 文字列リテラルとして再エスケープして格納**してしまう。レスポンスの `payload` を見ると以下のような二重エンコード状態:

```
"payload":"\"{\\\"target_type\\\":\\\"price\\\",\\\"target_id\\\":1,...}\""
```

承認フロー最終ステップの `ApplyWorkflowService.apply()` が `objectMapper.readValue(payload, ...)` を呼ぶが、既にエスケープ済みのため JSON として不正と判定され、`ResponseStatusException(UNPROCESSABLE_ENTITY, "Invalid payload JSON")` で 422 を返していた。

## なぜ CI で検知できなかったか

- フェーズ12 で**初めて**追加した `schema.sql` ファイル（直前まで Flyway migration のみ）。テストプロファイルでも自動的に拾われる挙動を見落とした。
- `WorkflowRequest.payload` を `columnDefinition = "JSON"` で書いた瞬間に、エンティティ型 `String` との非互換が発生する。**ローカルで `mvn test` を流す前にコミット → push** したためすり抜けた。
- 過去トラブル `a3c565cc fix(test): Phase 11導入による既存テストのApplicationContext失敗を修正` と同種の「Phase 導入時に Spring Context が H2 でロードできない」パターンが再発しており、ローカル `mvn test` をフェーズ完了の門番に置けていない。

## 修正内容

### `amazia-core/src/main/resources/schema.sql`
- `CREATE TABLE IF NOT EXISTS` のみ残し、`payload JSON` は維持（本番MySQL向けのため）。
- 「テスト環境ではこのファイルを読まない」前提に方針転換し、コメントを追記。

### `amazia-core/src/test/resources/application-test.properties`
- `spring.sql.init.schema-locations=` を空指定し、デフォルトの `classpath:schema.sql` 拾いを無効化。テストは `ddl-auto=create-drop` で JPA がエンティティからスキーマを生成する。

### `amazia-core/src/main/java/com/example/workflow/entity/WorkflowRequest.java`
- `payload` フィールドの `@Column(columnDefinition = "JSON")` を外し、`@Lob @Column(nullable = false)` に変更。
- 本番(MySQL)は V4 マイグレーションで `payload JSON NOT NULL` 作成済み・`ddl-auto=none` のため、エンティティ側 `columnDefinition` を外しても DDL に影響なし。Hibernate は素直に `String` を保存・取得するようになり、二重エスケープが解消。

## 結果

`mvn test` 全 14 テストクラス・103 ケースすべて成功。

```
[INFO] Tests run: 103, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## 再発防止

| 観点 | 対策 |
|------|------|
| MySQL 拡張構文の混入 | `schema.sql` を書くときは「H2 でも動くか」を必ず確認する。最低限 `CREATE TABLE` 内の `INDEX` 句は別ステートメント `CREATE INDEX` に分離するか、テストプロファイルから `schema-locations` を空にして除外する。 |
| プロファイル分離 | テスト環境(H2) と本番(MySQL) でロードする SQL を `application-{profile}.properties` の `spring.sql.init.schema-locations` で明示的に分ける。 |
| エンティティの JSON 列扱い | `String` フィールドに `columnDefinition = "JSON"` を付けない。JSON 値として扱いたいなら専用の `@JdbcTypeCode(SqlTypes.JSON)` か、自前の `AttributeConverter`、または素直に `@Lob` で TEXT 保存にする。 |
| ローカル門番 | フェーズ追加コミット前に必ず `mvn -pl amazia-core test` を実行する（CLAUDE.md「フェーズ完了の定義」と整合）。 |
