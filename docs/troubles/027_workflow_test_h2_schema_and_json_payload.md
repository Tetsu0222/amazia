# 027: フェーズ12 ワークフロー導入で CI（amazia-core テスト）が全滅

## ステータス
✅ 解決済（2026-05-05）／追加修正 ✅ 解決済（2026-05-09・派生①）

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
| エンティティの JSON 列扱い | `String` フィールドに `columnDefinition = "JSON"` を付けない。JSON 値として扱いたいなら専用の `@JdbcTypeCode(SqlTypes.JSON)` か、自前の `AttributeConverter`、または素直に `@Lob` で TEXT 保存にする。 ※**「`@Lob` で TEXT 保存」案は本番 MySQL の JSON カラムでは別の罠（CHARACTER SET binary）を踏むことが派生①で判明。下記「派生① 追記」を必ず参照。** |
| ローカル門番 | フェーズ追加コミット前に必ず `mvn -pl amazia-core test` を実行する（CLAUDE.md「フェーズ完了の定義」と整合）。 |

---

## 派生①: 本番 MySQL で `@Lob String` → JSON カラム保存が 3144 で 500 になる（2026-05-09）

### 発症箇所
- 本番 `POST /api/workflows`（Console 経由 `/console/api/workflows` も同根）
- 2026-05-09T04:32:13Z に管理コンソールから「価格変更」申請を投げた瞬間に 500 を返却

### 症状
レスポンス：
```json
{"timestamp":"2026-05-09T04:32:13.094+00:00","status":500,"error":"Internal Server Error","path":"/api/workflows"}
```
Core ログ（SSM 経由で取得）：
```
SQL Error: 3144, SQLState: 22001
Data truncation: Cannot create a JSON value from a string with CHARACTER SET 'binary'.
[insert into workflow_requests (... payload ...) values (?, ...)]
org.springframework.dao.DataIntegrityViolationException
  → com.mysql.cj.jdbc.exceptions.MysqlDataTruncation
```

### 根本原因
027 修正で `WorkflowRequest.payload` を `@Lob @Column(nullable = false) String` に変更したが、MySQL Connector/J 8.x は `@Lob String` を **CHARACTER SET 'binary'** で送るバインドモードを使う。MySQL の JSON カラムは値が `utf8mb4` でないと `JSON_VALID()` が成立せず、`ER_INVALID_JSON_CHARSET (3144)` を返す。

H2 はこの検証を持たないためテストでは検知できなかった。

### なぜ CI で検知できなかったか
- テストは H2(`ddl-auto=create-drop`)で動き、JSON 型の文字セット検証が存在しない
- 027 の再発防止表で「`@Lob` で TEXT 保存にする」を推奨していたが、本番 MySQL JSON カラムとの組み合わせを未検証のまま採用
- 本番疎通テストが CI に組み込まれておらず、Phase 12 デプロイ後に「申請」を実機で叩くまで気付かなかった

### 修正内容
`amazia-core/src/main/java/com/example/workflow/entity/WorkflowRequest.java`
- `@Lob` を削除し、`@Column(nullable = false, columnDefinition = "json")` に変更
- これで Hibernate は LONGVARCHAR 経路でなく VARCHAR 系として `utf8mb4` で値をバインドし、MySQL は JSON として正常受領
- `ddl-auto=none` の本番では `columnDefinition` は DDL に効かず、H2 + create-drop でも H2 は `"json"` 型を VARCHAR 互換に解釈するため二重エスケープは再発しない（`mvn -Dtest='Workflow*Test'` 13/13 成功で確認）

### 再発防止
| 観点 | 対策 |
|------|------|
| `@Lob` × MySQL JSON 列 | エンティティの `String` フィールドに `@Lob` を付けない。MySQL Connector/J が CHARACTER SET binary で送るため JSON カラムと相性が悪い。`columnDefinition="json"` を素直に明示する。 |
| 027 再発防止表の更新 | 027「再発防止」表で示した `@Lob` 推奨は誤誘導だった。同表に派生①参照の注記を追記。今後 027 を参照する人は派生①も合わせて読む。 |
| 本番疎通スモーク | 実装・運用パターン的に新規エンドポイントは「本番デプロイ後に1回手で叩く」では不足。少なくとも Console UI から申請までを通す手順を `phaseN_implementation_plan.md` の「フェーズ完了の定義」チェックボックスに含める。 |

### AI協働観点
- AI の判断ミス：027 修正時、`@Lob` 採用が MySQL JSON カラムで動くかを確認しないまま「素直に `@Lob` で TEXT 保存」を推奨した。MySQL Connector/J の文字セット挙動の知識が浅かった
- 人間が止めるべきだった点：027 の修正後に H2 テスト緑だけで本番マージしており、「JSON 列に対する本番 INSERT のスモーク」を踏まずに済ませてしまった
- 該当アンチパターン：AP-001（H2 と本番 RDBMS の挙動差をテスト不足のまま見過ごす同型再発）。AP-001 の出典欄に本派生を追加。
