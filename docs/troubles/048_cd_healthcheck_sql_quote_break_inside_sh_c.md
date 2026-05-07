# 048: CD ヘルスチェックの SQL シングルクォートが `sh -c '<INNER>'` を閉じて `Unknown column 'amazia'` で失敗

## ステータス
✅ 解決済（2026-05-07）

## 発症箇所
- ワークフロー: [.github/workflows/deploy.yml](../../.github/workflows/deploy.yml) `ヘルスチェック - 主要テーブル存在確認` ステップ（phaseX-6 で追加）の主クエリ
- 同型バグ: 同ステップの「不足テーブル特定」DIFF クエリ
- 同 jq テンプレート構造を持つ「本番 DB スキーマスナップショット保存」(`mysqldump`) は SQL リテラルを含まないため対象外

## 症状
046 修正後に再開した CD で、`主要テーブル存在確認` ステップが以下のログを残して `exit 1` で失敗:

```
===== 主要テーブル存在確認 SQL: StatusDetails =====
Failed
===== StandardOutputContent =====

===== StandardErrorContent =====
Warning: arning] Using a password on the command line interface can be insecure.
ERROR 1054 (42S22) at line 1: Unknown column 'amazia' in 'where clause'
failed to run commands: exit status 1
Error: 主要テーブル存在確認 SQL の実行に失敗（Status=Failed）
Error: Process completed with exit code 1.
```

`Using a password on the command line interface` が出ているため **046 の修正は効いている**（`$MYSQL_ROOT_PASSWORD` がコンテナまで届いている）。エラーは別系統で、`amazia` がカラム名として MySQL に届いている。

## 根本原因
046 修正で `docker exec amazia-mysql sh -c '<INNER>'` のシングルクォート方式に切り替えたが、`<INNER>` の中に書く SQL 本文に `'amazia'` や `IN ('users','products',...)` といった **SQL リテラル用シングルクォートが含まれている** ことを考慮していなかった。

最終的に SSM 経由で EC2 ホスト bash に届く文字列は概ねこうなる:

```
docker exec amazia-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" amazia -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='amazia' AND table_name IN ('users','products',...);"'
```

ホスト bash がこれを左から走査すると、`sh -c '` で開いたシングルクォートが SQL 中の `'` で次々に閉じられ、結果として MySQL に届く SQL は

```sql
SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=amazia AND table_name IN (users,products,...);
```

となり、`amazia` がカラム名として解釈されて `Unknown column 'amazia' in 'where clause'` (ER_BAD_FIELD_ERROR) で落ちる。

## なぜ046修正後にこれが残ったか
046 の対応は「`$MYSQL_ROOT_PASSWORD` という1個のシェル特殊文字（`$`）をホスト展開から守る」**点の対症療法** だった。

「`sh -c '<INNER>'` の `<INNER>` に持ち込まれる文字列」という構造全体に対する不変条件 — すなわち **`<INNER>` がシングルクォートを含まないこと**、含むなら `'\''` でエスケープすること — を整理しなかった。INNER に SQL 本文を埋め込むテンプレートを温存したため、SQL リテラル中の `'` が次のクォート構造破壊として表面化した。

phaseX-6 の「故意失敗テストデプロイ」が未実施で、本クエリが一度も実機で成功実行されないままマージされていたことも遠因。**ユーザー側に残っていた「故意失敗テストデプロイ」運用検証 (`project_post_deploy_schema_healthcheck.md` 参照) は、認証クォートだけでなくこの SQL クォート再発も同時に検証する観点を兼ねるべきだった**。

## なぜ CI で検知できなかったか
- 046 と同じ理由：SSM RunShellScript 経由の docker exec はローカル / CI 環境で再現できない
- コマンド文字列組み立てのユニットテストも存在せず、`sh -c '...'` の中で渡される文字列の二重クォートをハンドで追う必要があった
- phaseX-6 では「主要テーブル存在確認 SQL の実機 dry-run」を CD パイプラインに組み込んでおらず、初回本番デプロイで認証エラー（046）と SQL クォートエラー（048）を順番に踏むしかなかった

## 修正内容
[.github/workflows/deploy.yml](../../.github/workflows/deploy.yml) の「主要テーブル存在確認」ステップで、INNER に SQL を埋め込む直前に **bash の文字列置換** で SQL 中の `'` を `'\''` にエスケープする。`mysqldump` ステップは SQL リテラルを含まないため対象外。

```bash
# ホスト bash で展開した時に SQL 中の ' が外側 sh -c '...' を閉じないようにするため、
# 全ての ' を '\'' にエスケープしておく（POSIX シェル標準のシングルクォートエスケープ手法）。
APOS="'"
ESC_APOS="'\\''"
SQL_ESC="${SQL//$APOS/$ESC_APOS}"
INNER_SH="mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" amazia -N -e \"$SQL_ESC\""
```

`sed`/`printf` でリテラル `'` を組むやり方も試したが、shell エスケープと sed エスケープの噛み合わせで実機での復元結果が崩れる事例を確認したため、**bash パラメータ展開 + 変数経由** に統一した（GitHub Actions の `run: |` は `shell: /usr/bin/bash -e` のため bash 機能を前提にできる）。

DIFF クエリも同様に `${DIFF_SQL//$APOS/$ESC_APOS}` で対処（同じ run ブロックなので APOS/ESC_APOS は共有）。

最終的に SSM 上の bash には:
```
docker exec amazia-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" amazia -N -e "SELECT ... WHERE table_schema='\''amazia'\'' AND table_name IN ('\''users'\'',...);"'
```
が届き、コンテナ内 sh から見たとき MySQL には正しく `WHERE table_schema='amazia' AND table_name IN ('users',...)` が渡る。

検証は GitHub Actions と同じ bash で `eval` まで通し、最終的に MySQL に届く文字列がリテラルとして復元されることを確認した。

## 再発防止
| 観点 | 対策 |
|------|------|
| 046 で「クラス問題」を見抜けなかった反省 | `docs/ai_context/operational_insights.md` に **「`docker exec ... sh -c '<INNER>'` に持ち込む文字列の3不変条件」** を明記する。①外側を `'...'` シングルクォートで包むこと（046）②`<INNER>` 中の `'` を `'\''` でエスケープすること（048）③`<INNER>` 中の `\` を含むケースは別途検討すること、の3点。**046 修正時にも本来この3点をクラス全体として整理すべきだった**ことを訂正書きで補強する |
| ヘルスチェック未検証のままマージ | phaseX-6 残タスクの「故意失敗テストデプロイ」を**046・048 両方の検証を兼ねる前提で**実施する。具体的には ① schema.sql から1テーブル削除し不足検知の `exit 1` を確認 ② パスワード経路（046）と SQL クォート経路（048）が両方通ることを兼ねて検証 |
| クォート方向の同型バグ系統 | `docs/ai_context/test_insights.md` の「シェルクォート設計レビュー観点」に、"`sh -c '<INNER>'` を新規追加する PR では INNER に持ち込む全文字列の特殊文字（`'` `\` `$`）の扱いをレビュー時に列挙する" を追記 |
| 046 ドキュメントの整合 | `docs/troubles/046_*.md` の再発防止表に「**訓戒**：本対応は `$MYSQL_ROOT_PASSWORD` だけ守った部分対策で、INNER 中の `'` は 048 で再発した」を追記 |

---

## 派生不具合: `tr -d '[:space:]'` が `\n` も削って `EXPECTED_COUNT=1` 誤検知（2026-05-07 同日）

### 症状
048 修正後の再々デプロイで、`主要テーブル存在確認` ステップが今度はこのログで失敗:

```
期待: 1 件 / 実測: 0 件
===== 不足テーブル =====
usersrolespermissionsrole_permissionsrefresh_tokenspassword_reset_tokens...（改行なしで33テーブル名連結）
Error: 主要テーブルが 1 件中 0 件しか存在しない
Error: Process completed with exit code 1.
```

`期待: 1 件` が決定的シグナル。`required_tables.txt` には 33 テーブルあるはずなのに 1 件と数えられている。不足テーブル表示が改行なしの長文字列になっていることも符合する。

### 根本原因
[.github/workflows/deploy.yml](../../.github/workflows/deploy.yml) の `TABLES=$(grep -vE '...' "$REQUIRED_FILE" | tr -d '[:space:]' | tr '\n' ',' | sed 's/,$//')` で、`tr -d '[:space:]'` が POSIX `[:space:]` クラス（`space \t \n \r \v \f`）を全削除しているため、ファイル全体から `\n` も消える。続く `tr '\n' ','` は対象がもう存在せず無効化、`TABLES` は `usersrolespermissions...` という改行ゼロの 1 個の文字列に潰れる。

意図は「行末空白 / CR を削る」だったが、`[:space:]` に `\n` が含まれることが見落とされていた。

### なぜ046修正・048修正後まで気付けなかったか
phaseX-6 当時から壊れていたバグだが、**046（認証失敗）→ 048（SQL クォート破壊）の順で stdout に何も到達せず**、`grep`/`tr` パイプの結果を一度も実機で観察できなかった。本ステップは phaseX-6 マージから 2026-05-07 の3度目のデプロイまで一度も成功実行されておらず、046 と 048 を直してようやく到達した。

### 修正内容
`tr -d '[:space:]'` を `tr -d ' \t\r'` に変更し、`\n` を削除対象から外す。これで各行の末尾空白・CR は削られつつ、行区切りは保たれる。

```bash
# 旧: 行ごと潰れて TABLES が 1 個の連結文字列になる
TABLES=$(... | tr -d '[:space:]' | tr '\n' ',' | sed 's/,$//')
# 新: 各行のスペース・タブ・CR のみ削除し \n は残す
TABLES=$(... | tr -d ' \t\r' | tr '\n' ',' | sed 's/,$//')
```

DIFF クエリ側で使う `REQUIRED=$(echo "$TABLES" | tr ',' '\n' | sort -u)` は TABLES 修正で連動して正常化（comm に渡る両側が改行区切りに戻る）。

### 再発防止
- **`[:space:]` を使うシェル処理レビュー観点**: `tr -d '[:space:]'` / `sed 's/[[:space:]]//g'` のような **空白クラス全削除** は、行ベースのストリーム処理では `\n` まで巻き込むため意図しない結果になる。「行末を整えたい」なら `tr -d ' \t\r'` のように対象文字を明示する規約を `test_insights.md` に追記する
- **派生バグの早期発見**: phaseX-6 のヘルスチェック「故意失敗テストデプロイ」運用検証は、046・048 の経路に加え、**スクリプト全体が成功パスを 1 度通ること**（要件件数が正しく数えられること、不足テーブル表示が改行されることの目視確認）も検証項目に含める
- **横展開**: deploy.yml 内の他の `tr -d '[:space:]'` パターンが無いか念のため監査（本件以外には存在せず — 本ファイルの該当ステップのみ）
