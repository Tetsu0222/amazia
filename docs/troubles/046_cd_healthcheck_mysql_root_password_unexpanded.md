# 046: CD ヘルスチェックの MySQL 認証が `Access denied (using password: NO)` で失敗

## ステータス
✅ 解決済（2026-05-07）

## 発症箇所
- ワークフロー: [.github/workflows/deploy.yml](../../.github/workflows/deploy.yml) `ヘルスチェック - 主要テーブル存在確認` ステップ（phaseX-6 で追加）
- 同型バグ: 同ワークフローの「不足テーブル特定」DIFF クエリ・「本番 DB スキーマスナップショット保存」(`mysqldump`) ステップ

## 症状
CD のデプロイ後ヘルスチェックステップが以下のログを残して `exit 1` で失敗し、デプロイ自体が成功扱いにならない:

```
===== 主要テーブル存在確認 SQL: StatusDetails =====
Failed
===== StandardOutputContent =====

===== StandardErrorContent =====
Enter password: ERROR 1045 (28000): Access denied for user 'root'@'localhost' (using password: NO)
failed to run commands: exit status 1
Error: 主要テーブル存在確認 SQL の実行に失敗（Status=Failed）
Error: Process completed with exit code 1.
```

`Enter password:` が出ているのは `mysql -p` がインタラクティブにパスワード入力を待った跡。`using password: NO` は最終的にパスワード未指定で認証へ進んだことを示す。

## 根本原因
`docker exec amazia-mysql sh -c "..."` の **外側がダブルクォート** で組まれており、SSM RunShellScript が EC2 ホスト側 bash で `$MYSQL_ROOT_PASSWORD` を先に展開していた。EC2 ホスト側にはこの環境変数が無いため空文字に展開され、コンテナへ届くコマンドが実質 `mysql -uroot -p amazia ...` になってインタラクティブ入力に落ちていた。

具体的には旧実装の jq テンプレート:

```jq
("docker exec amazia-mysql sh -c \"mysql -uroot -p$MYSQL_ROOT_PASSWORD amazia -N -e \\\"" + $sql + "\\\"\"")
```

から SSM に渡される command 文字列は

```
docker exec amazia-mysql sh -c "mysql -uroot -p$MYSQL_ROOT_PASSWORD amazia -N -e \"...\""
```

となり、EC2 bash がダブルクォート内 `$MYSQL_ROOT_PASSWORD` を展開（→ 空文字）してから docker exec に渡してしまう。コンテナ内 sh から見た時点で既にパスワードは消失している。

## なぜ CI で検知できなかったか
- phaseX-6 のステップは「本番 EC2 上で SSM 経由 docker exec」という構造で、ローカル / CI 環境では一切再現できない。GitHub Actions のジョブ層ではコマンド組み立てが正常に見え、初回デプロイ時に初めて発覚する種類の不具合。
- コマンド組み立てユニットテストも存在せず（テストとしての価値が薄いため）、組み立て後のシェルクォート方向のミスは目視レビューに依存していた。

## 修正内容
[.github/workflows/deploy.yml](../../.github/workflows/deploy.yml) の3箇所の jq テンプレートを「`docker exec ... sh -c '<INNER>'`」のシングルクォート形式に変更。INNER 内では `$MYSQL_ROOT_PASSWORD` をダブルクォートで囲み、コンテナ内 sh で env から展開させる。

- ヘルスチェック - 主要テーブル存在確認: 主クエリ（COUNT）
- ヘルスチェック - 主要テーブル存在確認: 不足テーブル特定 DIFF クエリ
- 本番 DB スキーマスナップショット保存: `mysqldump`

例（COUNT クエリ）:

```bash
INNER_SH="mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" amazia -N -e \"$SQL\""
PARAMS_JSON=$(jq -n --arg inner "$INNER_SH" '{
  commands: [
    ("docker exec amazia-mysql sh -c '\''" + $inner + "'\''")
  ]
}')
```

最終的に SSM 上の bash には:
```
docker exec amazia-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" amazia -N -e "SELECT ..."'
```
が届くため、`$MYSQL_ROOT_PASSWORD` はホストでは展開されずコンテナ内 sh で解決される。

## 再発防止
| 観点 | 対策 |
|------|------|
| SSM 経由 docker exec のクォート方向 | コンテナ内環境変数を使うときは外側 `sh -c '...'` をシングルクォート固定にする運用ルールを `docs/ai_context/operational_insights.md` に追記 |
| 同型ワークフロー追加時 | `docker exec ... sh -c "...$VAR..."` 形式（ダブルクォート）はホスト側展開で空文字化するため、レビューで検出する観点を `test_insights.md` に追記 |
| ヘルスチェックの自己テスト不在 | 故意に schema.sql を欠損させたテストデプロイで改善① の `exit 1` を確認する運用検証（phaseX-6 残タスク）に、本ケースの認証クォート再発も併せて検証する観点を含める |

## 訓戒（[048](048_cd_healthcheck_sql_quote_break_inside_sh_c.md) 起因の事後追記）
本対応は `$MYSQL_ROOT_PASSWORD` という **1つの特殊文字（`$`）を守った点の対症療法** にすぎなかった。`docker exec ... sh -c '<INNER>'` という構造に持ち込まれる文字列のクラス全体に対する不変条件まで踏み込んでいなかったため、`<INNER>` に SQL 本文を埋め込むテンプレートが温存され、SQL リテラルの `'` が外側シングルクォートを閉じてしまうという同型バグ（[048](048_cd_healthcheck_sql_quote_break_inside_sh_c.md)）を後日踏んだ。

横展開の正しいやり方は **「同じファイル内の同じパターンを直す」（点）ではなく「`sh -c '<INNER>'` に持ち込む全文字列が満たすべき不変条件」（クラス）まで整理すること**。具体的には:

1. **外側を `'...'` シングルクォートで包むこと**（046 で対応）
2. **`<INNER>` 中の `'` を `'\''` でエスケープすること**（048 で対応）
3. **`<INNER>` 中の `\` を含むケースは別途検討すること**（将来課題）

3不変条件は `docs/ai_context/operational_insights.md` に明記。本トラブル単独で考えるのではなく、このクラス全体を意識して以後のシェル構築 PR をレビューする。
