# フェーズX-6：デプロイ後 主要テーブル存在確認 + 起動 WARN ログ抽出

## ステータス
🔲 未着手

## 位置付け
時系列フェーズ（1〜20）に依存しない横断的品質改善フェーズ。
027・037・038・044 と継続再発する **H2 / 本番 MySQL のスキーマ乖離** および 044 で顕在化した
**`continue-on-error` で潰された WARN がデプロイ後に検知できない** 構造的盲点に対する直接対策。

phaseX-2 で導入したデプロイ後ヘルスチェック（HTTP 200 確認）を、**スキーマ層の健全性** にまで拡張する位置付け。

---

## 背景・なぜ今やるか

### 構造的盲点

```
schema.sql の DDL 失敗
  └─ continue-on-error=true で WARN として潰される
      └─ Core 起動は成功（プロセスは生存）
          └─ HTTP ヘルスチェック（HTTP 200）も通る
              └─ 該当テーブルが呼ばれた瞬間に 1146 / 23000 等で 500
                  └─ ユーザーが踏むまで気付けない（サイレント故障）
```

### 実例（時系列）

| # | 日付 | 内容 |
|---|-----|------|
| 027 | 2026-05-06 | `schema.sql` の MySQL 専用構文が H2 で爆発（テスト時に検知できた版） |
| 037 | 2026-05-06 | `db/migration/V*.sql` を Flyway 経由と誤認 → 6 テーブル（payment_methods 等）が本番未作成・注文確定 API が 500 |
| 038 | 2026-05-07 | `products.price/stock` の NOT NULL 残存（旧 ALTER 漏れ）・Console 商品登録で 1048 |
| 044 | 2026-05-07 | `operation_logs.user_id BIGINT` と本番 `users.id BIGINT UNSIGNED` の FK 型不整合 → DDL 失敗が WARN で潰され該当テーブル不在のまま稼働 |

H2 + `ddl-auto=create-drop` のテストでは **本番 MySQL 固有の制約・型・履歴的ドリフト** が再現されないため、CI ではどれも検知不能。
特に 044 はユーザーが画面から踏むまで気付けない静かなサイレント故障で、CI/CD が長期間コケていた間に積み上がった schema 変更が一斉に本番に届いた結果でもある。

### なぜ次フェーズで対応するか

phase14.5 / phase15 が一段落したタイミングで、**個別バグへの対症療法では同型再発が止まらない** ことが 4 例目の事実として確定した。
[20260507_trouble_analysis.md](../../analysis/20260507_trouble_analysis.md) §「次の品質改善フェーズに送る再発防止策」で整理した方針を、独立した品質改善フェーズとして実装に落とす。

---

## 着手前提条件

- 進行中の機能フェーズ（phase15 完了 / phase16 着手前のタイミングが理想）と並行着手しない
- 本フェーズ着手時点で本番 DB スキーマと `schema.sql` を一度棚卸しし、既知のドリフトは事前に解消しておく（044 のようなホットフィックス済 ALTER は schema.sql に追記済かを確認）
- phaseX-2 のデプロイ後ヘルスチェック（HTTP 200 確認）は実装済前提

---

## 設計判断のサマリ

| 項目 | 現状 | 本フェーズの判断 | 判断理由 |
|------|------|------------------|---------|
| デプロイ後ヘルスチェック | HTTP 200 確認のみ | **+ 主要テーブル存在確認 SQL** | スキーマ層のサイレント故障を検知 |
| 起動時 schema.sql の WARN | コンテナログに残るが見られない | **CD ジョブログに `grep` 抽出** | `continue-on-error` の盲点に対する補助的対策 |
| 主要テーブル一覧の管理 | 未管理 | **専用定数ファイルを新設** | deploy.yml の肥大化を避ける／レビュー差分を局所化 |
| WARN 検出時の挙動 | — | **`::warning::` でジョブログにマーク表示するのみ（fail させない）** | 既知の無害 WARN を阻害要因にせず気付ける運用 |
| schema.sql レビュー観点 | 暗黙知 | **`operational_insights.md` カテゴリ3 に明文化** | FK 型一致・冪等性・H2/MySQL 互換の3点 |
| 本番スキーマスナップショット | 取得していない | **無料枠 S3 に日次保存（mysqldump --no-data）** | Entity / schema.sql との継続差分検知 |

**コスト試算：恒久 $0**
- S3 標準 5GB 無料枠内（DDL のみは数十 KB / 日 × 30 日 ≒ 数 MB）
- 既存 SSM 経由の `aws ssm send-command` 追加分のみで API 呼び出し料金も無料枠内

---

## スコープ

### 対象範囲

- `.github/workflows/deploy.yml` にステップ 4 件追加（後述）
- 新規ファイル `ops/healthcheck/required_tables.txt`（または `.json` / `.yaml`）を追加し、主要テーブル一覧を定数管理
- `docs/ai_context/operational_insights.md` カテゴリ3 を「既存実装と環境設定の棚卸し」に拡張し、schema.sql レビュー観点を追記
- `docs/CLAUDE.md` の「DB / API 設計書のメンテナンスルール」に**主要テーブル定数ファイルの同期更新ルール**を追加
- `docs/ai_context/test_insights.md` カテゴリ7-2 / カテゴリ10 への観点追記

### スコープ外

- CloudWatch Agent / SNS による通知系（ジョブログとリポジトリ通知で当面まかなう）
- Entity と本番 MySQL の自動差分検知ツール導入（schema.sql のレビュー規律と日次スナップショットで代替）
- マイグレーション機構の刷新（Flyway / Liquibase 導入は別フェーズで議論）
- スナップショット差分の自動アラート（差分は週次レビューで人手確認）

---

## 改善策

> 4 件はすべて単独でも導入可能だが、**①②セット**が中核で、③④は中長期の規律と観測の底上げ。

### 改善① デプロイ後ヘルスチェックに「主要テーブル存在確認」を追加

**対応内容**：deploy.yml の HTTP 200 確認の後段に、Core 経由で MySQL に接続し主要テーブル群が存在することを `information_schema` で確認するステップを追加。期待件数を満たさない場合は CD ジョブを `exit 1` で失敗させる。

#### 主要テーブル一覧の定数管理

deploy.yml に直接ベタ書きすると以下の問題が出る：
- 1 ファイルが肥大化してレビュー時の差分が読みにくい
- テーブル追加時に「設計書 + schema.sql + deploy.yml」の三点更新になり、`docs/database_design/` だけ更新して deploy.yml を忘れる事故が起きる
- shell 文字列内の長大リストはエスケープが事故の元

専用ファイルとして切り出す：

```
ops/
└── healthcheck/
    └── required_tables.txt    # 主要テーブル一覧（1 行 1 テーブル）
```

`ops/healthcheck/required_tables.txt`（例）：
```
# Amazia 本番 DB の主要テーブル一覧
# 1 行 1 テーブル / # 始まりはコメント / 空行は無視
# 追加・削除時は CLAUDE.md の「DB / API 設計書のメンテナンスルール」§主要テーブル定数の同期 に従う

# 認証・権限
users
permissions
roles
role_permissions

# 商品
products
product_skus
product_sku_prices
product_sku_stocks
product_sku_stock_transactions
product_sku_images

# 顧客（Market）
market_customers
market_sessions
market_failed_attempts
market_password_reset_tokens

# 注文・配送
payment_methods
address
sales
sales_return
shipping_statuses
shipping_methods
deliveries

# 監査
operation_logs

# ワークフロー
workflows
```

> ファイル形式は `.txt`（コメント可・grep しやすい）を第一候補とする。`.json` / `.yaml` 案もあったが、shell から扱う際の依存（jq / yq）が増えるため不採用。

#### deploy.yml への追加ステップ

deploy.yml の既存「ヘルスチェック（HTTP 200 確認）」ステップの直後、`if: success()` で実行する。

```yaml
- name: ヘルスチェック - 主要テーブル存在確認
  if: success()
  run: |
    set -euo pipefail

    # 主要テーブル一覧の読み込み（コメント・空行は除外）
    TABLES=$(grep -vE '^\s*(#|$)' ops/healthcheck/required_tables.txt | tr '\n' ',' | sed 's/,$//')
    EXPECTED_COUNT=$(echo "$TABLES" | tr ',' '\n' | wc -l)

    # SQL 用に IN 句を組み立て（'a','b','c' 形式）
    IN_LIST=$(echo "$TABLES" | sed "s/,/','/g")
    IN_LIST="'${IN_LIST}'"

    SQL="SELECT COUNT(*) FROM information_schema.tables \
         WHERE table_schema='amazia' AND table_name IN (${IN_LIST});"

    # SSM 経由で MySQL コンテナに接続
    CID=$(aws ssm send-command \
      --instance-ids "${{ secrets.EC2_INSTANCE_ID }}" \
      --region ap-southeast-2 \
      --document-name "AWS-RunShellScript" \
      --parameters "commands=[\"docker exec amazia-mysql mysql -uroot -p\$MYSQL_ROOT_PASSWORD amazia -N -e \\\"${SQL}\\\"\"]" \
      --query 'Command.CommandId' --output text)

    # コマンド完了待ち（最大 60 秒）
    for i in $(seq 1 12); do
      sleep 5
      ST=$(aws ssm get-command-invocation \
        --command-id "$CID" \
        --instance-id "${{ secrets.EC2_INSTANCE_ID }}" \
        --region ap-southeast-2 \
        --query 'Status' --output text)
      [ "$ST" = "Success" ] && break
      [ "$ST" = "Failed" ] || [ "$ST" = "TimedOut" ] && break
    done

    if [ "$ST" != "Success" ]; then
      aws ssm get-command-invocation \
        --command-id "$CID" \
        --instance-id "${{ secrets.EC2_INSTANCE_ID }}" \
        --region ap-southeast-2 \
        --query '{out:StandardOutputContent,err:StandardErrorContent,details:StatusDetails}'
      echo "ERROR: 主要テーブル存在確認 SQL の実行に失敗"
      exit 1
    fi

    ACTUAL_COUNT=$(aws ssm get-command-invocation \
      --command-id "$CID" \
      --instance-id "${{ secrets.EC2_INSTANCE_ID }}" \
      --region ap-southeast-2 \
      --query 'StandardOutputContent' --output text | tr -d '[:space:]')

    echo "期待: ${EXPECTED_COUNT} 件 / 実測: ${ACTUAL_COUNT} 件"

    if [ "$ACTUAL_COUNT" -lt "$EXPECTED_COUNT" ]; then
      # 不足テーブルを特定して表示
      DIFF_SQL="SELECT table_name FROM information_schema.tables \
                WHERE table_schema='amazia' AND table_name IN (${IN_LIST}) \
                ORDER BY table_name;"
      echo "不足テーブルを特定中..."
      # （省略：実在テーブル一覧を取得して required との差分を表示）
      echo "ERROR: 主要テーブルが ${EXPECTED_COUNT} 件中 ${ACTUAL_COUNT} 件しか存在しない"
      exit 1
    fi

    echo "✅ 主要テーブル ${EXPECTED_COUNT} 件すべて存在を確認"
```

**MYSQL_ROOT_PASSWORD の渡し方**：既に EC2 上の `.env` で `MYSQL_ROOT_PASSWORD` が解決されているため、SSM のシェル側で `$MYSQL_ROOT_PASSWORD` を展開させる（GitHub Secrets には別途 `MYSQL_ROOT_PASSWORD` を登録しない方針 = 鍵管理の単純化）。

**実装上の注意**：
- 044 で「DDL 失敗が WARN で潰される」事象を見たため、本ステップは **`::error::` で明示失敗** させる
- 失敗時のジョブログには「不足テーブル名」「Core 起動 WARN（次の②と統合してもよい）」を併記する
- 主要テーブル一覧の更新が漏れた場合は **過大検知（OK が NG になる）はしない**（テーブル追加 = 期待件数増えるだけで実装ガード）

---

### 改善② Core 起動ログの WARN を CD ジョブログに `grep` 抽出

**対応内容**：deploy.yml の `docker compose up -d` 完了直後、Core コンテナのログから schema.sql 関連の WARN を `grep` 抽出し、検出されたらジョブログに `::warning::` でマーク表示する（fail させない）。

```yaml
- name: 起動ログの schema 関連 WARN 抽出
  if: success()
  run: |
    set -uo pipefail

    # 起動から最大 60 秒待機して直近のログを取得
    sleep 30

    CID=$(aws ssm send-command \
      --instance-ids "${{ secrets.EC2_INSTANCE_ID }}" \
      --region ap-southeast-2 \
      --document-name "AWS-RunShellScript" \
      --parameters 'commands=["docker logs --since 5m amazia-core 2>&1 | grep -iE \"WARN.*(sql\\.init|schema|DDL|ALTER|CREATE TABLE|FOREIGN KEY)\" || true"]' \
      --query 'Command.CommandId' --output text)

    # 完了待ち（省略：①と同じパターン）
    # ...

    OUT=$(aws ssm get-command-invocation \
      --command-id "$CID" \
      --instance-id "${{ secrets.EC2_INSTANCE_ID }}" \
      --region ap-southeast-2 \
      --query 'StandardOutputContent' --output text)

    if [ -n "$(echo "$OUT" | tr -d '[:space:]')" ]; then
      echo "::warning::Core 起動時に schema 関連の WARN が検出されました。詳細を確認してください。"
      echo "===== Core 起動 schema 関連 WARN ====="
      echo "$OUT"
      echo "======================================"
    else
      echo "✅ Core 起動 schema 関連 WARN なし"
    fi
```

**判定基準**：
- 検出 = `::warning::` で GitHub Actions 上に警告マーク（**デプロイは成功扱い**）
- 既知の無害 WARN（例：H2 では出ない MySQL 専用の互換性警告）を許容しつつ、新種の WARN にも気付ける運用
- 将来「許容リスト方式」に格上げする余地は残すが、本フェーズでは導入しない（保守コスト > 得られる精度）

**フェーズ内では grep パターンを最小限**（`sql.init` / `schema` / `DDL` / `ALTER` / `CREATE TABLE` / `FOREIGN KEY`）に絞り、運用しながら追加・除外する。

---

### 改善③ schema.sql 編集時のレビュー観点を `operational_insights.md` に明文化

**対応内容**：`docs/ai_context/operational_insights.md` カテゴリ3 を「既存実装と環境設定の棚卸し」に拡張し、schema.sql 編集時の3点レビュー観点を追記する（037 提案の派生）。

**追記する観点**：

1. **FK 列の signed/unsigned を参照先と一致させる**
   - 044 起因。本プロジェクトでは Laravel migration 由来で `users.id` が `BIGINT UNSIGNED`
   - Spring の `Long` フィールドを `@JoinColumn` する場合は schema.sql 側で `BIGINT UNSIGNED` に揃える
2. **冪等性の担保**
   - `CREATE TABLE IF NOT EXISTS`
   - `INSERT IGNORE` または `ON DUPLICATE KEY UPDATE`
   - `ALTER TABLE` は再実行されても無害な形に（カラム追加なら `IF NOT EXISTS` 等価のチェックを SQL でなく `continue-on-error` で吸収する設計を理解しておく）
3. **H2 / MySQL 互換性**（027 起因）
   - `INDEX ...` インライン句は H2 で「不明なデータ型」になるため `CREATE INDEX` で分離
   - JSON 列 / `columnDefinition` の使い分け
   - `DATETIME(6)` 等の精度指定の扱い

**設計書の「前提」セクションへの裏付け参照ファイル要求**（037 派生）：
- 設計書に「前提」として書く事実は、**裏付け参照ファイルを 1 つ以上引用する**ことを CLAUDE.md に追記
- 例：「Flyway で管理」と書くなら `pom.xml` の依存ブロックを引用する

---

### 改善④ 本番 MySQL スキーマスナップショットを S3 に日次保存

**対応内容**：CD の最終ステップ（または独立した cron）で `mysqldump --no-data --no-tablespaces amazia` を取得し、`s3://fullstack-renaissance-demo/schema-snapshots/YYYY-MM-DD.sql` に保存。

**保存方針**：

| 項目 | 値 |
|------|-----|
| 取得タイミング | 各デプロイ完了直後（同一日 2 回目以降は上書き） |
| 保存先 | `s3://fullstack-renaissance-demo/schema-snapshots/YYYY-MM-DD.sql` |
| 保持期間 | S3 ライフサイクルで 90 日保持 → Glacier Deep Archive 移行（コスト 0 運用維持） |
| サイズ | DDL のみは 1 ファイル数十 KB（90 日で数 MB） |

**運用イメージ**：
- 週次レビューで `aws s3 ls` から直近 7 日分を取得し `diff` で `schema.sql` との差分を確認
- 037・038・044 のような「本番だけドリフト」を週次で発見できる
- 自動アラートは入れない（本フェーズスコープ外）

**deploy.yml ステップ例**：

```yaml
- name: 本番 DB スキーマスナップショット保存
  if: success()
  run: |
    DATE=$(date -u +%Y-%m-%d)
    CID=$(aws ssm send-command \
      --instance-ids "${{ secrets.EC2_INSTANCE_ID }}" \
      --region ap-southeast-2 \
      --document-name "AWS-RunShellScript" \
      --parameters "commands=[\"docker exec amazia-mysql mysqldump --no-data --no-tablespaces -uroot -p\$MYSQL_ROOT_PASSWORD amazia | aws s3 cp - s3://fullstack-renaissance-demo/schema-snapshots/${DATE}.sql\"]" \
      --query 'Command.CommandId' --output text)
    # 完了待ち（省略）
    echo "✅ スキーマスナップショット保存: s3://fullstack-renaissance-demo/schema-snapshots/${DATE}.sql"
```

**S3 ライフサイクルルール**（コンソール or 別 IaC で設定）：
- prefix: `schema-snapshots/`
- 90 日後 Glacier Deep Archive へ移行
- 365 日後 Expire

---

## ステップ一覧

| # | ステップ | 対象 | 内容 |
|---|---------|------|------|
| 1 | 主要テーブル一覧の整理 | docs / ops | `docs/database_design/README.md` のファイル一覧表から現存テーブルを洗い出し、`ops/healthcheck/required_tables.txt` を新規作成 |
| 2 | CLAUDE.md 規約追加 | docs | 「DB / API 設計書のメンテナンスルール」§主要テーブル定数の同期 を追記（テーブル追加・削除時は `required_tables.txt` も同フェーズ内で更新） |
| 3 | deploy.yml に改善① 追加 | .github/workflows | 主要テーブル存在確認ステップを HTTP 200 確認の直後に追加。fail 動作を確認 |
| 4 | deploy.yml に改善② 追加 | .github/workflows | Core 起動 WARN 抽出ステップを追加。`::warning::` 表示の動作を確認 |
| 5 | operational_insights.md 拡張 | docs/ai_context | カテゴリ3 を「既存実装と環境設定の棚卸し」に拡張し、schema.sql 編集時の3点観点を追記。設計書「前提」の裏付け参照ファイル要求を追加 |
| 6 | test_insights.md 観点追記 | docs/ai_context | カテゴリ7-2（H2/MySQL 互換）に「FK 列の signed/unsigned を参照先と一致」を追記。カテゴリ10 に「主要テーブル存在確認をデプロイ後ヘルスチェックで実施」を追記 |
| 7 | S3 ライフサイクル設定 | AWS | `schema-snapshots/` prefix に 90日 → Glacier、365日 → Expire のルール設定 |
| 8 | deploy.yml に改善④ 追加 | .github/workflows | スキーマスナップショット保存ステップを追加 |
| 9 | 故意に DDL 失敗を起こすテストデプロイ | EC2 | 本番影響のないブランチで「主要テーブルから 1 件削除」「`schema.sql` に意図的な型不整合を仕込む」を試して①②が正しく検知することを確認 |
| 10 | ドキュメント整理 | docs | troubles/README.md 再発防止アクション表のステータスを「✅ X-6にて対応」に更新 |

---

## TDD テストケース（運用テスト）

deploy.yml の改修なので JUnit / PHPUnit はないが、運用上の確認シナリオを設計書の TDD 相当として残す。

### 改善① 主要テーブル存在確認

| ケース | 操作 | 期待 |
|--------|------|------|
| 全テーブル存在 | 通常デプロイ | 「✅ 主要テーブル N 件すべて存在を確認」が出力され成功 |
| 1 件不足 | 本番 MySQL から `operation_logs` を `DROP TABLE` してデプロイ | 「ERROR: 主要テーブルが N 件中 N-1 件しか存在しない」で `exit 1` |
| 一覧ファイル不在 | `required_tables.txt` を一時的に削除してデプロイ | `grep` が空を返し EXPECTED_COUNT=0 → 過剰検知しない（成功扱い）。ただし PR レビューでファイル削除を catch する規約 |
| MySQL 接続失敗 | MySQL コンテナを止めてデプロイ | SSM コマンドが Failed → 「ERROR: 主要テーブル存在確認 SQL の実行に失敗」で `exit 1` |

### 改善② 起動 WARN 抽出

| ケース | 操作 | 期待 |
|--------|------|------|
| WARN なし | 通常デプロイ | 「✅ Core 起動 schema 関連 WARN なし」が出力され成功 |
| FK 不整合の WARN | `schema.sql` に意図的な FK 型不整合を仕込んでデプロイ | `::warning::` でマーク表示され WARN 内容がジョブログに残る。**デプロイは成功** |
| 大量 WARN | テスト環境で WARN を多数発生させる | ジョブログにすべて出力され、表示が崩れない（30000 文字超過時の挙動を確認） |

### 改善④ スナップショット保存

| ケース | 操作 | 期待 |
|--------|------|------|
| 通常保存 | 通常デプロイ | `s3://.../schema-snapshots/YYYY-MM-DD.sql` が更新される |
| 同日 2 回目 | 同日 2 回デプロイ | 同名ファイルが上書きされる（履歴は不要） |
| ライフサイクル | 90 日後の挙動確認 | Glacier Deep Archive に移行されている |

---

## 完了条件

- [ ] `ops/healthcheck/required_tables.txt` が新規作成され、`docs/database_design/README.md` のテーブル一覧と矛盾しないこと
- [ ] CLAUDE.md「DB / API 設計書のメンテナンスルール」に主要テーブル定数の同期更新ルールが追記されていること
- [ ] deploy.yml に改善①②④の3ステップが追加されていること
- [ ] 故意に DDL 失敗を起こすテストデプロイで改善①が `exit 1` で検知できること
- [ ] 故意に WARN を出すテストデプロイで改善②が `::warning::` で検知できること
- [ ] 改善④で S3 にスキーマスナップショットが保存されること（90日ライフサイクル設定済）
- [ ] `docs/ai_context/operational_insights.md` カテゴリ3 が拡張され、schema.sql 編集時の3点観点が追記されていること
- [ ] `docs/ai_context/test_insights.md` カテゴリ7-2 / カテゴリ10 に新観点が追記されていること
- [ ] `docs/troubles/README.md` 再発防止アクション表の該当2行（高優先：主要テーブル存在確認 / Core 起動 WARN 抽出）が「✅ X-6 にて対応」に更新されていること

---

## 期待効果

| 効果 | 影響範囲 |
|------|---------|
| 037・044 のような「テーブル不在」系トラブルが**デプロイ後 1 分以内に検知可能**になる | 高（CD ジョブが赤くなるためユーザーが踏む前に止まる） |
| 038 のような「制約ドリフト」系トラブルも WARN ログで先行検知できる可能性が高まる | 中（MySQL 1048 は実行時のみで完全ではないが、起動時 ALTER の WARN は捕まる） |
| 本番 MySQL のスキーマスナップショットが日次で残るため、週次レビューで `schema.sql` との差分を継続検知できる | 中（人手レビューが前提だが、現状の「気付かない」状態よりは大幅改善） |
| schema.sql レビュー観点が `operational_insights.md` に明文化されるため、新規フェーズ着手時に同型再発を予防できる | 低（規律としての底上げ） |

---

## 想定リスクと対応

| リスク | 対応 |
|--------|------|
| 主要テーブル一覧の更新漏れ | テーブル追加時の運用チェックリストを CLAUDE.md に組み込み、PR テンプレートに「`required_tables.txt` を更新したか」を追加検討（次フェーズ） |
| `::warning::` の WARN が常時出る運用になる | 一定期間（2週間程度）の運用で頻発する WARN は許容リスト化を検討。ただし本フェーズスコープ外 |
| MYSQL_ROOT_PASSWORD の取り扱い | EC2 上の `.env` 解決のみで完結し、GitHub Secrets には新規追加しない（鍵管理の単純化） |
| ヘルスチェック増加でデプロイ時間が伸びる | 各ステップ最大 60 秒の完了待ちを足しても全体で +2〜3 分程度。phaseX-2 で達成した「5分未満」は超えるが、品質トレードオフとして許容 |
| S3 ライフサイクルの設定漏れで料金発生 | 90 日 → Glacier、365 日 → Expire を確実に設定。設定後 1 ヶ月後に Cost Explorer で実費 0 を確認 |

---

## 参考リンク

- 元分析: [20260507_trouble_analysis.md](../../analysis/20260507_trouble_analysis.md) §「次の品質改善フェーズに送る再発防止策」
- 関連トラブル:
  - [027](../../troubles/027_workflow_test_h2_schema_and_json_payload.md) — H2/MySQL DDL 互換
  - [037](../../troubles/037_flyway_misassumed_phase14_tables_missing.md) — Flyway 誤認・テーブル不在
  - [038](../../troubles/038_products_price_stock_not_null_drift.md) — NOT NULL 残存
  - [044](../../troubles/044_operation_logs_table_missing_users_id_unsigned_drift.md) — FK 型不整合・WARN 潰し
- 既存ヘルスチェック: [phaseX-2](phaseX-2_deploy_pipeline_redesign.md) §改善④ / §完了条件
- 知見集: [test_insights.md](../../ai_context/test_insights.md) / [operational_insights.md](../../ai_context/operational_insights.md)
- 規約: [CLAUDE.md](../../../CLAUDE.md) §DB / API 設計書のメンテナンスルール

---

## メモリ連動

本設計書の作成に伴い、以下のメモリエントリを更新する想定：

- `project_post_deploy_schema_healthcheck.md` — 本書のリンクを参照先として追記
- 完了時に `feedback` メモリへ「主要テーブル存在確認をデプロイ後ヘルスチェックに含めることが H2/本番 MySQL 乖離対策として有効だった」を記録するか判断（実運用で再発防止が確認できた段階で）
